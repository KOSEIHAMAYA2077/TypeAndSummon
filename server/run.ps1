# Server startup (Windows PowerShell)
param(
    [switch]$NoKillExisting
)

$ErrorActionPreference = "Stop"
$RootDir = $PSScriptRoot
Set-Location $RootDir

$OutDir = Join-Path $RootDir "out"
$SqliteJar = Join-Path $RootDir "lib\sqlite-jdbc.jar"
$PortUtils = Join-Path $RootDir "scripts\ServerPortUtils.ps1"

if (-not (Test-Path $PortUtils)) {
    throw "Missing script: $PortUtils"
}
. $PortUtils

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac not found. Install a JDK and add it to PATH."
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java not found. Install a JDK or JRE and add it to PATH."
}

$ports = Get-ServerPortsFromEnv -RootDir $RootDir
if (-not $NoKillExisting) {
    Write-Host "Checking ports $($ports.HttpPort) (HTTP) and $($ports.SocketPort) (TCP)..."
    Stop-ListenersOnPorts -Ports @($ports.HttpPort, $ports.SocketPort)
}

& (Join-Path $RootDir "prepare_data.ps1")
& (Join-Path $RootDir "build.ps1")

Write-Host "Starting server (HTTP $($ports.HttpPort), TCP $($ports.SocketPort))..."
java -cp "$OutDir;$SqliteJar" Main
