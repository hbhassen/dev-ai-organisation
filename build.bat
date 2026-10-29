@echo off
rem DEVAI-005, DEVAI-133: verified complete Java and extension build.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" -Task Build
exit /b %errorlevel%
