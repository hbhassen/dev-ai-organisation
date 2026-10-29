@echo off
rem DEVAI-141, DEVAI-145: build, test and generate the installable VSIX.
python "%~dp0tools\build.py" --task Vsix
exit /b %errorlevel%
