@echo off
rem DEVAI-141, DEVAI-145: build, test and generate the installable VSIX.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" -Task Vsix
exit /b %errorlevel%
