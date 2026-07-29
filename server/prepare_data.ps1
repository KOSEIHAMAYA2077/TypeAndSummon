# Expand word DB archives and assign levels (Windows PowerShell)
$ErrorActionPreference = "Stop"
$RootDir = $PSScriptRoot
$DataDir = Join-Path $RootDir "data"
$LibDir = Join-Path $RootDir "lib"
$WordArchivePath = Join-Path $DataDir "english-valid-words.db.gz"
$WordDbPath = Join-Path $DataDir "english-valid-words.db"
$SqliteJar = Join-Path $LibDir "sqlite-jdbc.jar"
$ToolsOut = Join-Path $RootDir "out-tools"
$AssignSource = Join-Path $RootDir "scripts\AssignWordLevels.java"

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

function Expand-GzipFile {
    param(
        [string]$GzipPath,
        [string]$OutPath
    )
    Write-Host "Extracting $(Split-Path $GzipPath -Leaf)"
    $input = [System.IO.File]::OpenRead($GzipPath)
    try {
        $gzip = New-Object System.IO.Compression.GzipStream(
            $input,
            [IO.Compression.CompressionMode]::Decompress
        )
        try {
            $output = [System.IO.File]::Create($OutPath)
            try {
                $gzip.CopyTo($output)
            } finally {
                $output.Close()
            }
        } finally {
            $gzip.Close()
        }
    } finally {
        $input.Close()
    }
}


if (-not (Test-Path $WordDbPath)) {
    if (-not (Test-Path $WordArchivePath)) {
        throw "Missing archive: $WordArchivePath"
    }
    Expand-GzipFile -GzipPath $WordArchivePath -OutPath $WordDbPath
}

if (-not (Test-Path $SqliteJar)) {
    throw "Missing SQLite JDBC jar: $SqliteJar"
}

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac not found. Install a JDK and add it to PATH."
}

New-Item -ItemType Directory -Force -Path $ToolsOut | Out-Null
javac -encoding UTF-8 -d $ToolsOut $AssignSource
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$assignCp = "$ToolsOut;$SqliteJar"
java -cp $assignCp AssignWordLevels $WordDbPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Data preparation complete."
