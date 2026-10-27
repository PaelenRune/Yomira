# Run with PowerShell 7 on any platform: pwsh -File scripts/update-curated-repositories.ps1
# Suitable for cron / Task Scheduler / CI; exports are deliberately excluded from APK resources.
param(
    [string]$SourceIndexUrl = 'https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json',
    [string]$OutputRoot = (Join-Path $PSScriptRoot '../repositories')
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$response = Invoke-WebRequest -Uri $SourceIndexUrl -UseBasicParsing
$document = $response.Content | ConvertFrom-Json
$extensions = @($document.extensionList.extensions)
if ($extensions.Count -lt 100) { throw "Upstream index looks truncated ($($extensions.Count) entries)" }

function Convert-ToV1Entry($extension) {
    $language = if (@($extension.sources).Count -gt 0) { @($extension.sources)[0].language } else { 'all' }
    [ordered]@{
        name = $extension.name
        pkgName = $extension.packageName
        apkUrl = $extension.resources.apkUrl
        lang = $language
        nsfw = $(if ($extension.contentWarning -eq 'CONTENT_WARNING_NSFW') { 1 } else { 0 })
        version = $extension.versionName
        code = [long]$extension.versionCode
        sources = @($extension.sources | ForEach-Object {
            [ordered]@{ id = [string]$_.id; name = $_.name; lang = $_.language }
        })
    }
}

$westernPackageIds = @(
    'readcomicsonline', 'xoxocomics', 'comicland', 'nexcomic', 'cocomic',
    'comichubfree', 'digitalcomicmuseum', 'comicskingdom', 'comicfury',
    'hiveworks', 'theduckwebcomics', 'swordscomic', 'reallifecomics',
    'existentialcomics', 'darklegacycomics', 'snafucomic',
    'saturdaymorningbreakfastcomics', 'grrlpower', 'manta', 'webcomics'
)
$groups = @{ turkish = @(); western = @(); global = @() }
foreach ($extension in $extensions) {
    $group = if ($extension.packageName -like 'eu.kanade.tachiyomi.extension.tr.*') { 'turkish' }
        elseif ($westernPackageIds -contains $extension.packageName.Split('.')[-1]) { 'western' }
        else { 'global' }
    $groups[$group] += Convert-ToV1Entry $extension
}

function Write-Utf8($path, $text) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [IO.File]::WriteAllText($path, $text, [Text.UTF8Encoding]::new($false))
}

# Fixed-key XOR is obfuscation for casual APK inspection, NOT secret-key encryption.
# Encoding every index also conceals adult entries in otherwise general-purpose groups.
function Protect-Index($text) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($text)
    $key = [Text.Encoding]::UTF8.GetBytes('yomira-repository-v1')
    for ($i = 0; $i -lt $bytes.Length; $i++) { $bytes[$i] = $bytes[$i] -bxor $key[$i % $key.Length] }
    'YOMIRA-XOR-1:' + [Convert]::ToBase64String($bytes)
}

$catalog = @()
foreach ($group in @('turkish', 'western', 'global')) {
    $items = @($groups[$group] | Sort-Object pkgName)
    $json = ConvertTo-Json -InputObject $items -Depth 8 -Compress
    Write-Utf8 (Join-Path $OutputRoot "export/$group/index.min.json") $json
    Write-Utf8 (Join-Path $OutputRoot "$group/index.enc") (Protect-Index $json)
    # Remove only the superseded generated plaintext index, never arbitrary user files.
    $legacy = Join-Path $OutputRoot "$group/index.min.json"
    if (Test-Path $legacy) { Remove-Item -LiteralPath $legacy }
    $catalog += [ordered]@{
        resource = "$group/index.enc"
        indexUrl = "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/export/$group/index.min.json"
        upstreamUrl = $SourceIndexUrl
        group = $group
        westernPackages = $westernPackageIds
    }
    Write-Output "Wrote $($items.Count) entries: $group (bundled + clean export)"
}
Write-Utf8 (Join-Path $OutputRoot 'catalog.json') (ConvertTo-Json -InputObject $catalog -Depth 6 -Compress)
