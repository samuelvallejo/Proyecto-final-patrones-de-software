param([string]$RuntimeDirectory = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
$taskOllama = Join-Path $taskRoot '.tools/ollama'
$taskNgrok = Join-Path $taskRoot '.tools/ngrok'
New-Item -ItemType Directory -Force -Path $taskOllama,$taskNgrok | Out-Null
$ProgressPreference = 'SilentlyContinue'
if (-not (Test-Path -LiteralPath (Join-Path $taskOllama 'ollama.exe'))) {
    $taskRelease = 'https://github.com/ollama/ollama/releases/download/v0.35.0/'
    Invoke-WebRequest ($taskRelease + 'sha256sum.txt') -OutFile (Join-Path $taskOllama 'sha256sum.txt')
    Invoke-WebRequest ($taskRelease + 'ollama-windows-amd64.zip') -OutFile (Join-Path $taskOllama 'windows.zip')
    $taskExpected = ((Get-Content (Join-Path $taskOllama 'sha256sum.txt') | Where-Object { $_ -match 'ollama-windows-amd64.zip$' }) -split '\s+')[0]
    if ((Get-FileHash (Join-Path $taskOllama 'windows.zip') -Algorithm SHA256).Hash -ne $taskExpected) { throw 'Ollama checksum mismatch' }
    Expand-Archive -LiteralPath (Join-Path $taskOllama 'windows.zip') -DestinationPath $taskOllama -Force
}
if (-not (Test-Path -LiteralPath (Join-Path $taskNgrok 'ngrok.exe'))) {
    Invoke-WebRequest 'https://bin.ngrok.com/c/bNyj1mQVY4c/ngrok-v3-stable-windows-amd64.zip' -OutFile (Join-Path $taskNgrok 'windows.zip')
    Expand-Archive -LiteralPath (Join-Path $taskNgrok 'windows.zip') -DestinationPath $taskNgrok -Force
}
$taskSignature = Get-AuthenticodeSignature (Join-Path $taskNgrok 'ngrok.exe')
if ($taskSignature.Status -ne 'Valid' -or $taskSignature.SignerCertificate.Subject -notmatch 'ngrok') { throw 'Invalid ngrok publisher signature' }
Write-Output 'Official Ollama and ngrok runtimes are installed. Run start-local-ai.ps1 next.'
