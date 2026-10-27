# Regenerate the full, clean official catalog. Normal builds use the committed snapshot.
param(
    [string]$SourceIndexUrl = 'https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json',
    [string]$OutputRoot = (Join-Path $PSScriptRoot '../assets/keiyoushi')
)

$ErrorActionPreference = 'Stop'
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
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$json = ConvertTo-Json -InputObject $entries -Depth 8 -Compress
[IO.File]::WriteAllText((Join-Path $OutputRoot 'index.min.json'), $json, [Text.UTF8Encoding]::new($false))
Write-Output "Bundled $($entries.Count) Keiyoushi extensions as clean JSON"
