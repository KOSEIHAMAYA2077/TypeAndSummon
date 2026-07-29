# Client startup (Windows PowerShell)
$ErrorActionPreference = "Stop"
$RootDir = $PSScriptRoot
Set-Location $RootDir

$OutDir = Join-Path $RootDir "out"
$LibDir = Join-Path $RootDir "lib"
$MainClass = "ui.SushiBattleGUI"

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac not found. Install a JDK and add it to PATH."
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java not found. Install a JDK or JRE and add it to PATH."
}

$classpath = $OutDir
if (Test-Path $LibDir) {
    Get-ChildItem -Path $LibDir -Filter *.jar | ForEach-Object {
        $classpath = "$classpath;$($_.FullName)"
    }
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Sources = Get-ChildItem -Recurse -Filter *.java -Path (Join-Path $RootDir "src") |
    ForEach-Object { $_.FullName }
javac -encoding UTF-8 -classpath $classpath -d $OutDir $Sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$JlayerJar = Join-Path $LibDir "jlayer-1.0.1.jar"
if (-not (Test-Path $JlayerJar)) {
    & (Join-Path $RootDir "scripts\fetch_jlayer.ps1")
}

Write-Host "Starting client (cwd: $(Get-Location))..."
java -classpath $classpath $MainClass
