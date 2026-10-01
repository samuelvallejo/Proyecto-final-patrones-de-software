$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskMavenDir = Join-Path $taskRoot '.tools/apache-maven-3.9.9'
if (-not (Test-Path -LiteralPath (Join-Path $taskMavenDir 'bin/mvn.cmd'))) {
    New-Item -ItemType Directory -Force -Path (Join-Path $taskRoot '.tools') | Out-Null
    $taskArchive = Join-Path $taskRoot '.tools/maven.zip'
    $taskUrl = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'
    Invoke-WebRequest -Uri $taskUrl -OutFile $taskArchive
    $taskExpected = (Invoke-WebRequest -Uri ($taskUrl + '.sha512')).Content.Trim().Split(' ')[0]
    if ((Get-FileHash -LiteralPath $taskArchive -Algorithm SHA512).Hash.ToLowerInvariant() -ne $taskExpected.ToLowerInvariant()) { throw 'Invalid Maven checksum' }
    Expand-Archive -LiteralPath $taskArchive -DestinationPath (Join-Path $taskRoot '.tools') -Force
}
Write-Output $taskMavenDir
