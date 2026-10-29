@echo off
rem DEVAI-145: start the packaged gateway with external configuration.
python "%~dp0start_gateway.py" %*
exit /b %ERRORLEVEL%
