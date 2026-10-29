[CmdletBinding()]
param([ValidateSet('Build','Vsix','Package')][string]$Task = 'Build')
# Builds and validates the deliverable from pinned dependencies. DEVAI-005, DEVAI-138, DEVAI-144, DEVAI-145.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$previousLocation = Get-Location

function Invoke-Checked {
    <# Runs a native build command and propagates any nonzero result. DEVAI-140, DEVAI-144. #>
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Executable" }
}

function Test-Delivery {
    <# Starts the actual engine and gateway and verifies health plus anonymous denial. DEVAI-145. #>
    param([string]$Java, [string]$Engine, [string]$Gateway)
    Invoke-Checked $Java @('-jar', $Engine, '--version')
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start(); $port = $listener.LocalEndpoint.Port; $listener.Stop()
    $smoke = Join-Path $projectRoot ('.artifacts/smoke/' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $smoke -Force | Out-Null
    $arguments = @('-jar', ('"' + $Gateway + '"'), "--server.port=$port", '--server.address=127.0.0.1', '--ods.oidc-issuer=', '--spring.profiles.active=local', '--spring.datasource.url=jdbc:h2:mem:smoke;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE')
    $process = Start-Process -FilePath $Java -ArgumentList $arguments -WorkingDirectory $smoke -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $smoke 'gateway.log') -RedirectStandardError (Join-Path $smoke 'gateway-error.log')
    try {
        $ready = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            $process.Refresh()
            if ($process.HasExited) { throw "Gateway exited before readiness; inspect ignored .artifacts/smoke logs." }
            try { $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/health/live" -TimeoutSec 2; if ($health.status -eq 'UP') { $ready = $true; break } } catch { Start-Sleep -Milliseconds 500 }
        }
        if (-not $ready) { throw 'Gateway smoke test timed out.' }
        $denied = 0
        try { Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$port/api/v1/capabilities?projectId=1" -TimeoutSec 5 | Out-Null } catch { if ($_.Exception.Response) { $denied = [int]$_.Exception.Response.StatusCode } }
        if ($denied -notin @(401,403)) { throw "Anonymous API denial failed: $denied" }
        Write-Host 'Package smoke: engine version, gateway startup, health and anonymous API denial passed.'
    } finally { $process.Refresh(); if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force; $process.WaitForExit(10000) | Out-Null } }
}

try {
    Set-Location $projectRoot
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $jdk = Split-Path (Split-Path $java)
    if (-not (Test-Path (Join-Path $jdk 'bin/javac.exe'))) { throw 'A complete JDK 21 must be on PATH.' }
    $javaVersion = & (Join-Path $jdk 'bin/javac.exe') -version 2>&1
    if ($javaVersion -notmatch 'javac 21\.') { throw 'Use JDK 21 on PATH for this reproducible build.' }
    $env:JAVA_HOME = $jdk
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
    [xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw
    $version = $pom.project.version
    $manifest = Get-Content -LiteralPath (Join-Path $projectRoot 'vscode-extension/package.json') -Raw | ConvertFrom-Json
    if ($version -ne $manifest.version) { throw 'Maven and VSIX versions differ.' }
    New-Item -ItemType Directory -Path '.artifacts' -Force | Out-Null
    Invoke-Checked $java @('tools/CheckDocumentation.java', $projectRoot)
    Invoke-Checked 'python' @('tools/check_python.py')
    Invoke-Checked 'python' @('-m','unittest','discover','-s','tools/tests','-v')
    Invoke-Checked (Join-Path $projectRoot 'mvnw.cmd') @('-B','-ntp','clean','verify')
    Set-Location (Join-Path $projectRoot 'vscode-extension')
    Invoke-Checked $npm @('ci','--no-audit','--no-fund')
    Invoke-Checked $npm @('run','compile')
    Invoke-Checked $npm @('run','lint')
    Invoke-Checked $npm @('test')
    Set-Location $projectRoot
    $engine = Join-Path $projectRoot "ods-java-core/target/ods-java-core-$version-engine.jar"
    $gateway = Join-Path $projectRoot "ods-gateway/target/ods-gateway-$version.jar"
    foreach ($artifact in @($engine,$gateway)) { if (-not (Test-Path $artifact) -or (Get-Item $artifact).Length -lt 10000) { throw "Missing or empty artifact: $(Split-Path $artifact -Leaf)" } }
    Test-Delivery $java $engine $gateway
    if ($Task -in @('Vsix','Package')) {
        New-Item -ItemType Directory -Path 'vscode-extension/engine' -Force | Out-Null
        Copy-Item -LiteralPath $engine -Destination 'vscode-extension/engine/ods-java-core.jar' -Force
        [IO.File]::WriteAllText((Join-Path $projectRoot 'vscode-extension/engine/ods-java-core.jar.sha256'), (Get-FileHash -LiteralPath $engine -Algorithm SHA256).Hash.ToLowerInvariant(), [Text.UTF8Encoding]::new($false))
        Set-Location (Join-Path $projectRoot 'vscode-extension')
        Invoke-Checked $npm @('run','package')
        Set-Location $projectRoot
        $vsix = Join-Path $projectRoot ".artifacts/organisation-development-standard-$version.vsix"
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($vsix)
        try { foreach ($entry in @('extension/package.json','extension/dist/extension.js','extension/engine/ods-java-core.jar','extension/engine/ods-java-core.jar.sha256','extension/media/view.js')) { if (-not $archive.GetEntry($entry)) { throw "VSIX entry missing: $entry" } } } finally { $archive.Dispose() }
        Write-Host "VSIX verified: .artifacts/organisation-development-standard-$version.vsix"
    }
    if ($Task -eq 'Package') {
        $suite = Join-Path $projectRoot 'suiteInstall'
        # Keep previous generated deliveries without deleting user or source files. DEVAI-145.
        if (Test-Path $suite) {
            if ([IO.Path]::GetFullPath($suite) -ne (Join-Path $projectRoot 'suiteInstall')) { throw 'Unexpected delivery directory.' }
            $previous = Join-Path $projectRoot ('.artifacts/previous-suite-' + [guid]::NewGuid().ToString('N'))
            Move-Item -LiteralPath $suite -Destination $previous
        }
        foreach ($part in @('bin','config','docs','scripts','examples')) { New-Item -ItemType Directory -Path (Join-Path $suite $part) -Force | Out-Null }
        Copy-Item -LiteralPath $engine,$gateway,$vsix -Destination (Join-Path $suite 'bin')
        Copy-Item -Path 'config/*.example' -Destination (Join-Path $suite 'config')
        Copy-Item -Path 'docs/*.md' -Destination (Join-Path $suite 'docs')
        Copy-Item -LiteralPath 'README.md','CHANGELOG.md','specification.md','prompt-developpement.md' -Destination (Join-Path $suite 'docs')
        Copy-Item -LiteralPath 'scripts/start-gateway.ps1','scripts/start-gateway.bat','scripts/install-extension.bat','scripts/prepare-prompt-bundle.ps1' -Destination (Join-Path $suite 'scripts')
        $exampleRoot = Join-Path $projectRoot 'examples'
        foreach ($example in Get-ChildItem -LiteralPath $exampleRoot -Recurse -File -Force) {
            if ($example.Extension -notin @('.json','.yml','.md','.py') -or $example.FullName -match '[\\/]__pycache__[\\/]') { continue }
            $relativeExample = $example.FullName.Substring($exampleRoot.Length + 1)
            $exampleTarget = Join-Path (Join-Path $suite 'examples') $relativeExample
            New-Item -ItemType Directory -Path (Split-Path $exampleTarget) -Force | Out-Null
            Copy-Item -LiteralPath $example.FullName -Destination $exampleTarget
        }
        [IO.File]::WriteAllText((Join-Path $suite 'README.md'), "# ODS $version`n`nStart with [installation](docs/INSTALLATION.md), [configuration](docs/CONFIGURATION.md), [validation](docs/VALIDATION.md) and [requirement coverage](docs/CONFORMITE.md).`n`nInstall the VSIX with scripts/install-extension.bat. Start the local gateway with scripts/start-gateway.bat. External services require organizational configuration.`n`nThis is a development delivery; consult BUILD.json for its exact source identity and SHA256SUMS for artifact digests.`n", [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $suite 'VERSION'), "$version`n", [Text.UTF8Encoding]::new($false))
        $sourceCommit = (& git rev-parse HEAD).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the source commit.' }
        $sourceState = (& git status --porcelain --untracked-files=normal | Out-String).Trim()
        $buildIdentity = @{ version=$version; sourceCommit=$sourceCommit; sourceDirty=[bool]$sourceState; builtAtUtc=[DateTime]::UtcNow.ToString('o') } | ConvertTo-Json
        [IO.File]::WriteAllText((Join-Path $suite 'BUILD.json'), $buildIdentity, [Text.UTF8Encoding]::new($false))
        foreach ($deliveredText in Get-ChildItem -LiteralPath $suite -Recurse -File | Where-Object { $_.Extension -in @('.md','.py','.ps1','.bat','.json','.yml','.example') }) {
            if ((Get-Content -LiteralPath $deliveredText.FullName -Raw -Encoding UTF8) -match '(?i)[A-Z]:\\Users\\|/home/[^<\s]+/|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----') { throw "Sensitive workstation data in delivery: $($deliveredText.Name)" }
        }
        $hashes = Get-ChildItem (Join-Path $suite 'bin') -File | ForEach-Object { '{0}  bin/{1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name }
        [IO.File]::WriteAllLines((Join-Path $suite 'SHA256SUMS'), $hashes, [Text.UTF8Encoding]::new($false))
        Test-Delivery $java (Join-Path $suite "bin/ods-java-core-$version-engine.jar") (Join-Path $suite "bin/ods-gateway-$version.jar")
        Compress-Archive -LiteralPath $suite -DestinationPath (Join-Path $projectRoot "suiteInstall-$version.zip") -Force
        Write-Host "Delivery verified: suiteInstall-$version.zip"
    }
    Write-Host "SUCCESS: $Task $version"
    exit 0
} catch { Write-Error $_; exit 1 } finally { Set-Location $previousLocation }
