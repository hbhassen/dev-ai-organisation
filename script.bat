@echo off
rem Rebuild and package the whole solution for one organisation profile. DEVAI-127, DEVAI-144.
setlocal
set "ODS_BUILD_PROFILE=%~1"
if not defined ODS_BUILD_PROFILE set "ODS_BUILD_PROFILE=%ODS_ORGANISATION_PROFILE%"
if not defined ODS_BUILD_PROFILE (
  echo Usage: script.bat ^<organisation.json^>
  echo Or define ODS_ORGANISATION_PROFILE.
  exit /b 2
)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build.ps1" -Task Package -OrganisationProfile "%ODS_BUILD_PROFILE%"
exit /b %errorlevel%
