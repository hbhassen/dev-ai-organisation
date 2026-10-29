@echo off
rem DEVAI-145: start the packaged gateway with external configuration.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-gateway.ps1" %*
exit /b %ERRORLEVEL%
