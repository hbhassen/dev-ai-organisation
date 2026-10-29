@echo off
rem DEVAI-142, DEVAI-143: build, test and assemble the standalone delivery.
python "%~dp0tools\build.py" --task Package
exit /b %errorlevel%
