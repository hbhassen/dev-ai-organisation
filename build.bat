@echo off
rem DEVAI-005, DEVAI-133: verified complete Java and extension build.
python "%~dp0tools\build.py" --task Build
exit /b %errorlevel%
