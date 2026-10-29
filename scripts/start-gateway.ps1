param([string]$Config = '')
# Loads external deployment settings without evaluating shell code. DEVAI-035, DEVAI-036, DEVAI-145.
$ErrorActionPreference = 'Stop'
$installRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $Config) { $Config = Join-Path $installRoot 'config/gateway.env' }
if (Test-Path -LiteralPath $Config) {
    foreach ($line in Get-Content -LiteralPath $Config) {
        if ($line.Trim() -eq '' -or $line.Trim().StartsWith('#')) { continue }
        if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid configuration line.' }
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$jars = @(Get-ChildItem -LiteralPath (Join-Path $installRoot 'bin') -Filter 'ods-gateway-*.jar' -File)
if ($jars.Count -ne 1) { throw 'Exactly one gateway JAR is required in bin.' }
Set-Location $installRoot
& java -jar $jars[0].FullName
exit $LASTEXITCODE
