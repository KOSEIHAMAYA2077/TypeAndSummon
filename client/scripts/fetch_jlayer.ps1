# Download JLayer (MP3) into client/lib if missing
$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$LibDir = Join-Path $RootDir "lib"
$JarPath = Join-Path $LibDir "jlayer-1.0.1.jar"
$Url = "https://repo1.maven.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1.jar"

New-Item -ItemType Directory -Force -Path $LibDir | Out-Null
if (Test-Path $JarPath) {
    Write-Host "Already exists: $JarPath"
    exit 0
}

Write-Host "Downloading jlayer-1.0.1.jar ..."
Invoke-WebRequest -Uri $Url -OutFile $JarPath -UseBasicParsing
Write-Host "Saved: $JarPath"
