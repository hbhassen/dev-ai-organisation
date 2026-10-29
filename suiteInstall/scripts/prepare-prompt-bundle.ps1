param([Parameter(Mandatory=$true)][string]$Destination, [long]$Version = 1)
# Prepares a reviewable unsigned example manifest with exact hashes; signing remains an administrative action. DEVAI-044, DEVAI-164.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (Test-Path -LiteralPath $Destination) { throw 'Choose a new destination to preserve existing bundles.' }
New-Item -ItemType Directory -Path $Destination | Out-Null
Copy-Item -Path (Join-Path $projectRoot 'examples/prompts/*.md') -Destination $Destination
function New-Layer {
    <# Computes immutable file references for a signed prompt layer. DEVAI-164. #>
    param([string]$Name,[string]$Mode)
    return @{ mode=$Mode; required=$true; files=@(@{path=$Name;sha256=(Get-FileHash -LiteralPath (Join-Path $Destination $Name) -Algorithm SHA256).Hash.ToLowerInvariant()}) }
}
$global = New-Layer 'global.md' 'APPEND'
$global.constraints = @{'tests.required'='true';'java.buildTool'='Maven'}
$manifest = [ordered]@{schemaVersion=1;bundleId='organisation-development';bundleVersion=$Version;origin='ORGANIZATION';issuedAt=[DateTime]::UtcNow.ToString('o');expiresAt=[DateTime]::UtcNow.AddDays(7).ToString('o');global=$global;stacks=@{JAVA=(New-Layer 'java.md' 'REPLACE')};unconfiguredStacks=@{useApprovedGeneric=$true};tasks=@{'organisation-review'=@{label='Revue organisationnelle';action='CHAT';layer=(New-Layer 'review.md' 'REPLACE')}}}
[IO.File]::WriteAllText((Join-Path $Destination 'bundle.json'), ($manifest | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
Write-Host 'Unsigned example bundle created. Review, sign and verify before deployment.'
