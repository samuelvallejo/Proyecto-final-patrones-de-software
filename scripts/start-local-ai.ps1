param([string]$RuntimeDirectory = (Split-Path -Parent $PSScriptRoot), [switch]$LocalOnly)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
$taskCloud = Join-Path $taskRoot '.local/cloud'
New-Item -ItemType Directory -Force -Path $taskCloud | Out-Null
$taskOllama = Join-Path $taskRoot '.tools/ollama/ollama.exe'
$taskNgrok = Join-Path $taskRoot '.tools/ngrok/ngrok.exe'
if (-not (Test-Path -LiteralPath $taskOllama) -or (-not $LocalOnly -and -not (Test-Path -LiteralPath $taskNgrok))) { throw 'Run install-local-ai.ps1 first' }
$taskJar = Join-Path $taskRoot 'backend/target/backend-1.0.0.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw 'Compile the Java backend with Maven first' }
$taskJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }
$taskModel = if ($env:OLLAMA_MODEL) { $env:OLLAMA_MODEL } else { 'qwen3:4b-instruct' }
$taskKeyPath = Join-Path $taskCloud 'local-ai-gateway.key'
if (-not (Test-Path -LiteralPath $taskKeyPath)) {
    $taskBytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($taskBytes)
    [IO.File]::WriteAllText($taskKeyPath, [BitConverter]::ToString($taskBytes).Replace('-','').ToLowerInvariant())
}
$taskKey = ([IO.File]::ReadAllText($taskKeyPath)).Trim()
if ($taskKey -notmatch '^[a-f0-9]{64}$') { throw 'Invalid local AI gateway key' }
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_MODELS = Join-Path $taskRoot '.local/ollama-models'
$env:OLLAMA_NUM_PARALLEL = '1'
$env:OLLAMA_MAX_LOADED_MODELS = '1'
$env:OLLAMA_NO_CLOUD = '1'
$env:OLLAMA_VULKAN = 'false'
try { $null = Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 2 } catch {
    $taskProcess = Start-Process -FilePath $taskOllama -ArgumentList 'serve' -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskCloud 'ollama.log') -RedirectStandardError (Join-Path $taskCloud 'ollama-error.log')
    $taskProcess.Id | Set-Content (Join-Path $taskCloud 'ollama.pid')
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 30; $taskAttempt++) {
        try { $null = Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 2; $taskReady = $true; break } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $taskReady) { throw 'Ollama did not start; inspect its private error log' }
}
$taskModels = Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 5
if ($taskModels.models.name -notcontains $taskModel) {
    & $taskOllama pull $taskModel
    if ($LASTEXITCODE -ne 0) { throw 'Local model download failed' }
}
$taskWarmup = @{ model = $taskModel; stream = $false; keep_alive = '30m'; options = @{ num_ctx = 8192 } } | ConvertTo-Json -Depth 4
$null = Invoke-RestMethod 'http://127.0.0.1:11434/api/generate' -Method Post -ContentType 'application/json' -Body $taskWarmup -TimeoutSec 120
$env:LOCAL_AI_TOKEN_FILE = $taskKeyPath
$env:OLLAMA_MODEL = $taskModel
$taskHeaders = @{ Authorization = "Bearer $taskKey" }
$taskGatewayHealth = $null
try { $taskGatewayHealth = Invoke-RestMethod 'http://127.0.0.1:11435/health' -Headers $taskHeaders -TimeoutSec 5 } catch { }
if ($taskGatewayHealth -and $taskGatewayHealth.model -ne $taskModel) { throw 'A gateway for another model is running. Stop the local AI processes before switching models.' }
if (-not $taskGatewayHealth) {
    if (Get-NetTCPConnection -LocalPort 11435 -State Listen -ErrorAction SilentlyContinue) { throw 'Gateway port is already occupied; inspect the running process before restarting.' }
    $taskRuntimeJar = Join-Path $taskCloud 'local-ai-runtime.jar'
    Copy-Item -LiteralPath $taskJar -Destination $taskRuntimeJar -Force
    $taskArgs = @('-Dloader.main=com.streamguard.ai.LocalAiGateway','-cp',('"' + $taskRuntimeJar + '"'),'org.springframework.boot.loader.launch.PropertiesLauncher')
    $taskProcess = Start-Process -FilePath $taskJava -ArgumentList $taskArgs -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskCloud 'local-ai-gateway.log') -RedirectStandardError (Join-Path $taskCloud 'local-ai-gateway-error.log')
    $taskProcess.Id | Set-Content (Join-Path $taskCloud 'local-ai-gateway.pid')
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 20; $taskAttempt++) {
        try { $null = Invoke-RestMethod 'http://127.0.0.1:11435/health' -Headers $taskHeaders -TimeoutSec 5; $taskReady = $true; break } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $taskReady) { throw 'Gateway did not start; inspect its private error log' }
}
$taskUrl = 'http://127.0.0.1:11435'
if (-not $LocalOnly) {
    $taskTokenPath = Join-Path $taskCloud 'ngrok-authtoken.key'
    if (-not (Test-Path -LiteralPath $taskTokenPath)) { New-Item -ItemType File -Path $taskTokenPath | Out-Null }
    $taskToken = ([IO.File]::ReadAllText($taskTokenPath)).Trim()
    if ($taskToken -notmatch '^[A-Za-z0-9_]{20,200}$') { throw "Save your ngrok authtoken in $taskTokenPath" }
    $taskConfig = Join-Path $taskCloud 'ngrok.yml'
    & $taskNgrok config add-authtoken $taskToken --config $taskConfig
    if ($LASTEXITCODE -ne 0) { throw 'Ngrok configuration failed' }
    try { $taskTunnels = Invoke-RestMethod 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 2 } catch { $taskTunnels = $null }
    $taskTunnel = @($taskTunnels.tunnels | Where-Object { $_.config.addr -eq 'http://127.0.0.1:11435' -and $_.public_url -like 'https://*' })
    if ($taskTunnel.Count -eq 0) {
        if ($taskTunnels) { throw 'Another ngrok agent already uses port 4040; stop that agent yourself first' }
        $taskArgs = @('http','http://127.0.0.1:11435','--inspect=false','--config',('"' + $taskConfig + '"'),'--log',('"' + (Join-Path $taskCloud 'ngrok.log') + '"'),'--log-format=json')
        $taskProcess = Start-Process -FilePath $taskNgrok -ArgumentList $taskArgs -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskCloud 'ngrok-stdout.log') -RedirectStandardError (Join-Path $taskCloud 'ngrok-error.log')
        $taskProcess.Id | Set-Content (Join-Path $taskCloud 'ngrok.pid')
        for ($taskAttempt = 0; $taskAttempt -lt 30; $taskAttempt++) {
            try { $taskTunnels = Invoke-RestMethod 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 2; $taskTunnel = @($taskTunnels.tunnels | Where-Object { $_.config.addr -eq 'http://127.0.0.1:11435' -and $_.public_url -like 'https://*' }); if ($taskTunnel.Count -gt 0) { break } } catch { }
            Start-Sleep -Seconds 1
        }
        if ($taskTunnel.Count -eq 0) { throw 'Ngrok did not connect; inspect its private error log' }
    }
    $taskUrl = $taskTunnel[0].public_url
}
$taskEnvPath = Join-Path $taskCloud 'local-ai-render.env'
[IO.File]::WriteAllText($taskEnvPath, "AI_PROVIDER=ollama`nOLLAMA_GATEWAY_URL=$taskUrl`nOLLAMA_GATEWAY_TOKEN=$taskKey`nOLLAMA_MODEL=$taskModel`n")
Write-Output "Local AI is ready at $taskUrl"
Write-Output "Private backend variables were saved in $taskEnvPath. Never publish that file."
Write-Output 'Keep this computer awake and online. Stop with scripts/stop-local-ai.ps1.'
