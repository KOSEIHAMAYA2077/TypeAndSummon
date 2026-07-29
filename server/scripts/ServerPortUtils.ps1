# Shared helpers: read .env ports and stop listeners before server start.

function Get-ServerPortsFromEnv {
    param([string]$RootDir)

    $httpPort = 8080
    $socketPort = 9090
    $envFile = Join-Path $RootDir ".env"
    if (-not (Test-Path $envFile)) {
        return [PSCustomObject]@{ HttpPort = $httpPort; SocketPort = $socketPort }
    }

    foreach ($line in Get-Content $envFile -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }
        $eq = $trimmed.IndexOf("=")
        if ($eq -lt 1) {
            continue
        }
        $key = $trimmed.Substring(0, $eq).Trim()
        $value = $trimmed.Substring($eq + 1).Trim().Trim('"').Trim("'")
        if ($key -eq "SERVER_PORT" -and $value -match "^\d+$") {
            $httpPort = [int]$value
        }
        if ($key -eq "SOCKET_PORT" -and $value -match "^\d+$") {
            $socketPort = [int]$value
        }
    }

    return [PSCustomObject]@{ HttpPort = $httpPort; SocketPort = $socketPort }
}

function Stop-ListenersOnPorts {
    param([int[]]$Ports)

    foreach ($port in $Ports) {
        $stopped = $false

        try {
            $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
            foreach ($conn in $connections) {
                $procId = $conn.OwningProcess
                if ($procId -gt 0) {
                    Write-Host "Stopping process $procId (listening on port $port)..."
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                    $stopped = $true
                }
            }
        } catch {
            # Fallback when Get-NetTCPConnection is unavailable.
        }

        if (-not $stopped) {
            $portPattern = ":$port\s"
            foreach ($line in (netstat -ano | Select-String "LISTENING" | Select-String $portPattern)) {
                $parts = ($line.ToString().Trim() -split '\s+')
                if ($parts.Count -lt 1) {
                    continue
                }
                $procId = 0
                [void][int]::TryParse($parts[$parts.Count - 1], [ref]$procId)
                if ($procId -gt 0) {
                    Write-Host "Stopping process $procId (listening on port $port)..."
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                    $stopped = $true
                }
            }
        }

        if ($stopped) {
            Start-Sleep -Milliseconds 400
        }
    }
}
