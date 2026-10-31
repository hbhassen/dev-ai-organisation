param([switch]$Build, [switch]$Install, [switch]$Open)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    docker compose up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed' }
    python bootstrap.py
    if ($LASTEXITCODE -ne 0) { throw 'Service initialization failed' }
    python provision.py
    if ($LASTEXITCODE -ne 0) { throw 'Provisioning/policy renewal failed; check service readiness' }
    if ($Build) {
        Push-Location ..
        try {
            python tools/build.py --task Build | Tee-Object -FilePath .artifacts/manual-build.log
            if ($LASTEXITCODE -ne 0) { throw 'Full build failed' }
            python TestDOKERMan/package_private.py
            if ($LASTEXITCODE -ne 0) { throw 'Private packaging failed' }
        } finally { Pop-Location }
    }
    if ($Install) {
        python vscode.py install
        if ($LASTEXITCODE -ne 0) { throw 'VSIX installation failed' }
        python vscode.py configure
        if ($LASTEXITCODE -ne 0) { throw 'Test profile configuration failed' }
    }
    if ($Open) {
        python vscode.py open
        if ($LASTEXITCODE -ne 0) { throw 'Test profile launch failed' }
    }
} finally { Pop-Location }
