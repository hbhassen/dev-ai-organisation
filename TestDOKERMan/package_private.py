"""Package an already validated build with the existing ODS packaging helpers."""
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT / 'tools'))
import build
import organisation_profile
import runtime_bundle


def main():
    log = ROOT / '.artifacts/manual-build.log'
    if not log.exists() or 'SUCCESS: Build 0.3.0' not in log.read_text(encoding='utf-16' if log.read_bytes().startswith(b'\xff\xfe') else 'utf8', errors='replace'):
        raise RuntimeError('A successful current full Build must be recorded in .artifacts/manual-build.log')
    source_paths = ['pom.xml', 'ods-contracts', 'ods-java-core', 'ods-gateway', 'vscode-extension', 'tools', 'scripts', 'config', 'prompt-content']
    subprocess.run(['git', 'diff', 'HEAD', '--exit-code', '--', *source_paths], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    engine = ROOT / 'ods-java-core/target/ods-java-core-0.3.0-engine.jar'
    gateway = ROOT / 'ods-gateway/target/ods-gateway-0.3.0.jar'
    for artifact in (engine, gateway):
        if not artifact.is_file() or artifact.stat().st_size < 10000 or artifact.stat().st_mtime > log.stat().st_mtime:
            raise RuntimeError('Missing artifact or artifact newer than validation evidence')
    profile = HERE / '.local/profile/organisation.json'
    data, environment = organisation_profile.load(profile)
    extension = ROOT / 'vscode-extension'
    (extension / 'engine').mkdir(exist_ok=True)
    shutil.copy2(engine, extension / 'engine/ods-java-core.jar')
    (extension / 'engine/ods-java-core.jar.sha256').write_text(build.digest(engine))
    stage = ROOT / '.artifacts' / ('manual-vsix-' + uuid.uuid4().hex[:12])
    organisation_profile.stage(ROOT, stage, profile)
    runtime_bundle.prepare(ROOT, stage / 'runtime', gateway, environment, data['id'])
    for item in data['files']:
        target = stage / 'runtime/config' / item['target']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(profile.parent / item['source'], target)
    runtime_bundle.inventory(stage / 'runtime')
    build.run(['node', extension / 'test/runtime-smoke.cjs', stage])
    build.run([*runtime_bundle.python_command(), '-B', ROOT / 'tools/vibe_contract.py', stage / 'runtime'])
    build.validate_private_package(stage)
    vsix = ROOT / '.artifacts/ods-docker-manual-0.3.0.vsix'
    build.run([extension / 'node_modules/.bin/vsce.cmd', 'package', '--target', runtime_bundle.target(), '--no-dependencies',
        '--allow-package-all-secrets', '--allow-package-env-file', '--allow-missing-repository', '--skip-license', '--out', vsix], cwd=stage)
    build.verify_vsix(vsix, '0.3.0', engine)
    suite = HERE / 'suiteInstall'
    if suite.exists():
        previous = HERE / '.local' / ('previous-suite-' + uuid.uuid4().hex)
        suite.rename(previous)
    for name in ('bin', 'config', 'docs'):
        (suite / name).mkdir(parents=True, exist_ok=True)
    for artifact in (engine, gateway, vsix):
        shutil.copy2(artifact, suite / 'bin')
    organisation_profile.deliver(profile, suite / 'config')
    shutil.copy2(HERE / '.local/tls/ca.crt', suite / 'config/ca.crt')
    shutil.copy2(HERE / 'README.md', suite / 'docs/MANUAL-TESTS.md')
    shutil.copy2(HERE / 'VALIDATION.md', suite / 'docs/VALIDATION-LOCAL.md')
    for name in ('INSTALLATION.md', 'CONFIGURATION.md'):
        shutil.copy2(ROOT / 'docs' / name, suite / 'docs' / name)
    (suite / 'VERSION').write_text('0.3.0\n')
    identity = build.identity(ROOT) | {'version': '0.3.0', 'profileId': data['id'], 'builtAtUtc': datetime.now(timezone.utc).isoformat(), 'distribution': 'PRIVATE_LOCAL_TEST_ONLY'}
    (suite / 'BUILD.json').write_text(json.dumps(identity, indent=2))
    (suite / 'README.md').write_text('# ODS 0.3.0 - livraison privee de test local\n\nInstaller bin/ods-docker-manual-0.3.0.vsix dans le profil Test. Les services attendus sont sur localhost. Voir docs/MANUAL-TESTS.md. Le VSIX contient les credentials du banc : ne pas publier cette archive. Le poste cible doit disposer de Java 21, Python 3.12 et des outils Maven/Git, et approuver config/ca.crt dans le JDK utilise. Aucun service Docker ni secret de signature prive ne figure dans cette livraison.\n')
    (suite / 'SHA256SUMS').write_text(''.join(build.digest(p) + '  bin/' + p.name + '\n' for p in sorted((suite / 'bin').iterdir())))
    build.test_delivery(HERE / '.local/jdk/bin/java.exe', suite / 'bin' / engine.name, suite / 'bin' / gateway.name)
    archive = HERE / 'suiteInstall-0.3.0.zip'
    if archive.exists():
        shutil.copy2(archive, HERE / '.local' / ('previous-suite-' + uuid.uuid4().hex + '.zip'))
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as output:
        for file in suite.rglob('*'):
            if file.is_file():
                output.write(file, file.relative_to(HERE))
    (HERE / '.local/stage-path.txt').write_text(str(stage))
    print('Private VSIX and suiteInstall-0.3.0.zip verified. No remote publication.')


if __name__ == '__main__':
    main()
