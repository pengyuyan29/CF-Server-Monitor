param([Parameter(Mandatory=$true)][string]$BlogDist, [string]$Output = './encrypted-blog.zip')
$ErrorActionPreference = 'Stop'
$dist = (Resolve-Path -LiteralPath $BlogDist).Path
$vault = Join-Path $dist 'vault.json'
$articles = Join-Path $dist 'articles'
if (!(Test-Path -LiteralPath $vault) -or !(Test-Path -LiteralPath $articles)) { throw 'Point BlogDist at the existing blog build dist folder, not the source folder.' }
$meta = Get-Content -LiteralPath $vault -Raw | ConvertFrom-Json
if ($meta.version -ne 1 -or $meta.iterations -ne 600000 -or !$meta.data) { throw 'Expected an encrypted blog vault; refusing to package plaintext sources.' }
Get-ChildItem -LiteralPath $articles -File -Recurse | ForEach-Object {
    if ($_.Name -notmatch '^[a-f0-9]{16}\.json$') { throw 'Unexpected file in encrypted articles folder.' }
    $article = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
    if (!$article.iv -or !$article.data -or $article.html) { throw 'Article package is not encrypted.' }
}
Compress-Archive -LiteralPath $vault,$articles -DestinationPath $Output -Force
Write-Output "Encrypted blog archive: $Output"
