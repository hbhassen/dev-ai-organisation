@echo off
rem DEVAI-144, DEVAI-145: install the single versioned VSIX in the delivery.
setlocal
for %%F in ("%~dp0..\bin\organisation-development-standard-*.vsix") do (
  call code --install-extension "%%~fF"
  if errorlevel 1 exit /b 1
)
exit /b 0
