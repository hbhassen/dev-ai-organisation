[CmdletBinding()]
param([ValidateSet('Build','Vsix','Package')][string]$Task = 'Build', [string]$OrganisationProfile = '')
# Compatibility entry point; batch scripts invoke Python directly. DEVAI-144, DEVAI-145.
$arguments = @((Join-Path $PSScriptRoot '../tools/build.py'), '--task', $Task)
if ($OrganisationProfile) { $arguments += @('--organisation-profile', $OrganisationProfile) }
& python @arguments
exit $LASTEXITCODE
