@echo off
setlocal
if not exist "%~dp0.tools\apache-maven-3.9.9\bin\mvn.cmd" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\bootstrap-maven.ps1"
if errorlevel 1 exit /b 1
call "%~dp0.tools\apache-maven-3.9.9\bin\mvn.cmd" %*
exit /b %errorlevel%
