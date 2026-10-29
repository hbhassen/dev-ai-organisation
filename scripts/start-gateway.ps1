param([string]$Config = '')
# Compatibility entry point; start-gateway.bat invokes Python directly. DEVAI-035, DEVAI-145.
$arguments = @((Join-Path $PSScriptRoot 'start_gateway.py'))
if ($Config) { $arguments += @('--config', $Config) }
& python @arguments
exit $LASTEXITCODE
