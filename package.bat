@echo off
rem DEVAI-142, DEVAI-143: build, test and assemble the standalone delivery.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" -Task Package
exit /b %errorlevel%
