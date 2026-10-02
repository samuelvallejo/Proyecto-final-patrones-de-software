param([string]$RuntimeDirectory = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
foreach ($taskName in @('ngrok','local-ai-gateway','ollama')) {
    $taskPidPath = Join-Path $taskRoot ".local/cloud/$taskName.pid"
    if (-not (Test-Path -LiteralPath $taskPidPath)) { continue }
    $taskPid = [int]([IO.File]::ReadAllText($taskPidPath).Trim())
    $taskProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$taskPid"
    if ($taskProcess) {
        $taskExpected = if ($taskName -eq 'local-ai-gateway') { 'com.streamguard.ai.LocalAiGateway' } else { Join-Path $taskRoot ".tools/$taskName/$taskName.exe" }
        $taskMatches = if ($taskName -eq 'local-ai-gateway') { $taskProcess.CommandLine -like "*$taskExpected*" -and $taskProcess.CommandLine -like ('*' + (Join-Path $taskRoot '.local/cloud/local-ai-runtime.jar') + '*') } else { $taskProcess.ExecutablePath -eq $taskExpected }
        if (-not $taskMatches) { throw "Recorded process for $taskName does not match; refusing to stop it" }
        Stop-Process -Id $taskPid
    }
    Remove-Item -LiteralPath $taskPidPath
}
Write-Output 'StreamGuard local AI processes stopped. The cloud application remains available for human moderation.'
