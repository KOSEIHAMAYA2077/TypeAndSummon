# Compile Java sources (Windows PowerShell)
$ErrorActionPreference = "Stop"
$RootDir = $PSScriptRoot
$OutDir = Join-Path $RootDir "out"

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac not found. Install a JDK and add it to PATH."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Sources = Get-ChildItem -Recurse -Filter *.java -Path (Join-Path $RootDir "src") |
    ForEach-Object { $_.FullName }

javac -encoding UTF-8 -d $OutDir $Sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Compiled Java sources into $OutDir"
