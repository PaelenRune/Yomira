# Regenerates the curated Yomira extension repository indexes under repositories/.
#
# Source of truth is the public Keiyoushi v2 index (index.json). The generated
# files use the v1 schema Yomira's KeiyoushiRepoParser understands:
#   { name, pkgName, apkUrl, lang, nsfw (0|1), version, code, sources[{id,name,lang}] }
#
# Run from any directory:  powershell -File scripts\update-curated-repositories.ps1
# Commit the two generated index.min.json files afterwards.

param(
    [string]$SourceIndexUrl = 'https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json',
    [string]$OutputRoot = (Join-Path $PSScriptRoot '..\repositories')
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$temp = Join-Path ([IO.Path]::GetTempPath()) 'yomira-keiyoushi-index.json'
Invoke-WebRequest -Uri $SourceIndexUrl -OutFile $temp -UseBasicParsing
# Read explicitly as UTF-8; Windows PowerShell would otherwise guess ANSI and
# corrupt source names like "Golge Bahcesi" / "Turkce Manga Oku".
$document = ([IO.File]::ReadAllText($temp, [Text.Encoding]::UTF8)) | ConvertFrom-Json
$extensions = @($document.extensionList.extensions)
if ($extensions.Count -lt 100) { throw "Upstream index looks truncated ($($extensions.Count) entries)" }

# Converts one Keiyoushi v2 entry into the v1 schema Yomira parses.
function Convert-ToV1Entry($extension) {
    $nsfw = if ($extension.contentWarning -eq 'CONTENT_WARNING_NSFW') { 1 } else { 0 }
    $language = if (@($extension.sources).Count -gt 0) { @($extension.sources)[0].language } else { 'all' }
    [ordered]@{
        name    = $extension.name
        pkgName = $extension.packageName
        apkUrl  = $extension.resources.apkUrl
        lang    = $language
        nsfw    = $nsfw
        version = $extension.versionName
        code    = [long]$extension.versionCode
        sources = @($extension.sources | ForEach-Object {
            [ordered]@{ id = $_.id; name = $_.name; lang = $_.language }
        })
    }
}

# Turkish local sources (fansub/scanlation extensions maintained upstream).
$turkish = @($extensions |
    Where-Object { $_.packageName -like 'eu.kanade.tachiyomi.extension.tr.*' } |
    ForEach-Object { Convert-ToV1Entry $_ } |
    Sort-Object pkgName)

# Western comic libraries: Marvel/DC style aggregators plus legitimate
# webcomic and comic-strip publishers. Adult-only sites are intentionally
# excluded from this curated list.
$westernPackageIds = @(
    'readcomicsonline', 'xoxocomics', 'comicland', 'nexcomic', 'cocomic',
    'comichubfree', 'digitalcomicmuseum', 'comicskingdom', 'comicfury',
    'hiveworks', 'theduckwebcomics', 'swordscomic', 'reallifecomics',
    'existentialcomics', 'darklegacycomics', 'snafucomic',
    'saturdaymorningbreakfastcomics', 'grrlpower', 'manta', 'webcomics'
)
$western = @($extensions |
    Where-Object { $westernPackageIds -contains $_.packageName.Split('.')[-1] } |
    ForEach-Object { Convert-ToV1Entry $_ } |
    Sort-Object pkgName)
if ($western.Count -ne $westernPackageIds.Count) {
    $found = @($western | ForEach-Object { $_.pkgName.Split('.')[-1] })
    $missing = @($westernPackageIds | Where-Object { $found -notcontains $_ })
    Write-Warning "Upstream no longer ships: $($missing -join ', ')"
}

function Write-CuratedIndex($items, $path) {
    $directory = Split-Path -Parent $path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    # UTF-8 without BOM: kotlinx.serialization rejects a leading BOM.
    [IO.File]::WriteAllText($path, (ConvertTo-Json $items -Depth 6 -Compress), [Text.UTF8Encoding]::new($false))
    Write-Output "Wrote $($items.Count) entries to $path"
}

Write-CuratedIndex $turkish (Join-Path $OutputRoot 'turkish\index.min.json')
Write-CuratedIndex $western (Join-Path $OutputRoot 'western\index.min.json')
