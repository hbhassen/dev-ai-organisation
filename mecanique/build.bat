@echo off
rem MECA-007: standalone tool validation, never invokes the extension build.
python "%~dp0build.py"
exit /b %errorlevel%
