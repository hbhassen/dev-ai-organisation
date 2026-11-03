@echo off
rem MECA-001: use the activated Python environment and forward explicit tool arguments.
python "%~dp0run.py" %*
exit /b %errorlevel%
