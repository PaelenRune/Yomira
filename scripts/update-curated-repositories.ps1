# Regenerate the curated repository snapshots bundled with the personal build.
# Universal: the full clean Keiyoushi catalog. Turkish/Western: curated slices of it.
# Normal builds use the committed snapshots; run this to refresh them.
param(
    [string]$SourceIndexUrl = 'https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json',
    [string]$OutputRoot = (Join-Path $PSScriptRoot '../assets'),
    [string]$HostedRoot = (Join-Path $PSScriptRoot '../repositories')
)

$ErrorActionPreference = 'Stop'
# Curated western packages (historical Yomira selection; extend deliberately).
$WesternPackages = @(
    'readcomicsonline', 'xoxocomics', 'comicland', 'nexcomic', 'cocomic', 'comichubfree',
    'digitalcomicmuseum', 'comicskingdom', 'comicfury', 'hiveworks', 'theduckwebcomics',
    'swordscomic', 'reallifecomics', 'existentialcomics', 'darklegacycomics', 'snafucomic',
    'saturdaymorningbreakfastcomics', 'grrlpower', 'manta', 'webcomics'
)

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$response = Invoke-WebRequest -Uri $SourceIndexUrl -UseBasicParsing
$document = $response.Content | ConvertFrom-Json
$extensions = @($document.extensionList.extensions)
if ($extensions.Count -lt 100) { throw "Upstream index looks truncated ($($extensions.Count) entries)" }
$entries = @($extensions | Sort-Object packageName | ForEach-Object {
    $extension = $_
    [ordered]@{
        name = $extension.name
        pkgName = $extension.packageName
        apkUrl = $extension.resources.apkUrl
        lang = $(if (@($extension.sources).Count -gt 0) { @($extension.sources)[0].language } else { 'all' })
        nsfw = $(if ($extension.contentWarning -eq 'CONTENT_WARNING_NSFW') { 1 } else { 0 })
        version = $extension.versionName
        code = [long]$extension.versionCode
        sources = @($extension.sources | ForEach-Object {
            [ordered]@{ id = [string]$_.id; name = $_.name; lang = $_.language }
        })
    }
})
if (@($entries.pkgName | Select-Object -Unique).Count -ne $entries.Count) { throw 'Duplicate packages' }

function Write-Index([string]$Path, $Items) {
    New-Item -ItemType Directory -Force -Path (Split-Path $Path) | Out-Null
    $json = ConvertTo-Json -InputObject $Items -Depth 8 -Compress
    [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
    $label = [IO.Path]::GetFileNameWithoutExtension($Path) -replace '\.min$', ''
    Write-Output "Wrote $label -> $Path ($(@($Items).Count) entries)"
}

$shortName = { param($pkg) $pkg.Substring($pkg.LastIndexOf('.') + 1) }
$turkish = @($entries | Where-Object { $_.lang -eq 'tr' -or @($_.sources.lang) -contains 'tr' })
$western = @($entries | Where-Object { (& $shortName $_.pkgName) -in $WesternPackages })
if ($turkish.Count -lt 20) { throw "Turkish slice looks truncated ($($turkish.Count) entries)" }
if ($western.Count -lt 15) { throw "Western slice looks truncated ($($western.Count) entries)" }

Write-Index (Join-Path $OutputRoot 'keiyoushi/index.min.json') $entries
Write-Index (Join-Path $OutputRoot 'repositories/turkish/index.min.json') $turkish
Write-Index (Join-Path $OutputRoot 'repositories/western/index.min.json') $western
# Hosted copies: the settings screen registers these raw.githubusercontent URLs.
Write-Index (Join-Path $HostedRoot 'turkish/index.min.json') $turkish
Write-Index (Join-Path $HostedRoot 'western/index.min.json') $western
Write-Output "Bundled $($entries.Count) Keiyoushi extensions ($($turkish.Count) Turkish, $($western.Count) Western) as clean JSON"
