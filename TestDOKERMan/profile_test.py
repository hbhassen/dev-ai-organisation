"""Run the installed extension in VS Code Test with an isolated, synthetic-only provider runtime."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
from vscode import executable

HERE = Path(__file__).resolve().parent
LOCAL = HERE / '.local'
sys.path.insert(0, str(HERE.parent / 'tools'))
from runtime_bundle import extended


def immutable_library(source, target):
    """Reuse unchanged library bytes on the same volume; fall back to copying across filesystems."""
    try:
        os.link(source, target)
        return target
    except OSError:
        return shutil.copy2(source, target)


def main():
    installed = Path(sys.argv[1]).resolve()
    manifest = json.loads((installed / 'package.json').read_text(encoding='utf8'))
    if manifest['publisher'] != 'ods-local' or manifest['name'] != 'ods-docker-manual':
        raise RuntimeError('The installed private manual-test extension is required')
    stage = LOCAL / ('automation-stage-' + uuid.uuid4().hex[:12])
    shutil.copytree(extended(installed), extended(stage), ignore=shutil.ignore_patterns('vibe', '__pycache__', '*.pyc'))
    runtime = stage / 'runtime'
    shutil.copytree(extended(installed / 'runtime/vibe'), extended(runtime / 'vibe'),
        copy_function=immutable_library, ignore=shutil.ignore_patterns('__pycache__', '*.pyc'))
    shutil.copy2(runtime / 'vibe_bridge.py', runtime / 'vibe_bridge_actual.py')
    shutil.copy2(HERE / 'vibe_http_fixture.py', runtime / 'vibe_bridge.py')
    (runtime / 'fixture-mode.txt').write_text('success')
    deployment = json.loads((runtime / 'deployment.json').read_text())
    deployment['environment'].update({'ODS_MISTRAL_URL': 'https://mistral.invalid',
        'ODS_VIBE_URL': 'https://mistral.invalid/v1', 'ODS_MISTRAL_API_KEY': 'synthetic-profile-test',
        'ODS_VIBE_TOKEN': 'synthetic-profile-test', 'ODS_VIBE_AUTH': 'API_KEY'})
    (runtime / 'deployment.json').write_text(json.dumps(deployment))
    inventory = json.loads((runtime / 'INTEGRITY.json').read_text())
    for name in ('deployment.json', 'vibe_bridge.py', 'vibe_bridge_actual.py', 'fixture-mode.txt'):
        digest = hashlib.sha256((runtime / name).read_bytes()).hexdigest()
        inventory[name] = digest
        if (runtime / (name + '.sha256')).exists():
            (runtime / (name + '.sha256')).write_text(digest)
            inventory[name + '.sha256'] = hashlib.sha256((runtime / (name + '.sha256')).read_bytes()).hexdigest()
    (runtime / 'INTEGRITY.json').write_text(json.dumps(inventory, indent=2))
    (LOCAL / 'automation-stage.txt').write_text(str(stage))
    environment = dict(os.environ)
    environment.pop('ELECTRON_RUN_AS_NODE', None)
    environment['JAVA_HOME'] = str(LOCAL / 'jdk')
    environment['PATH'] = str(LOCAL / 'jdk/bin') + os.pathsep + environment.get('PATH', '')
    environment['ODS_MANUAL_REAL_MISTRAL'] = '1' if '--real-mistral' in sys.argv[2:] else '0'
    args = [str(executable()), '--user-data-dir', str(LOCAL / 'vscode-data'), '--extensions-dir', str(LOCAL / 'extensions'),
        '--profile', 'Test', '--disable-workspace-trust', '--disable-extensions', '--new-window',
        '--extensionDevelopmentPath', str(installed), '--extensionTestsPath', str(HERE / 'profile-smoke.cjs'),
        str(LOCAL / 'workspaces/demo-allowed')]
    with (LOCAL / 'profile-test-host.log').open('w', encoding='utf8') as output:
        result = subprocess.run(args, env=environment, stdout=output, stderr=subprocess.STDOUT, timeout=900)
    print('Real VS Code Test extension-host process exit:', result.returncode)
    if result.returncode:
        raise SystemExit(result.returncode)
    evidence = json.loads((LOCAL / 'profile-smoke-result.json').read_text())
    if evidence['status'] != 'SUCCESS':
        raise RuntimeError('Extension-host assertions failed')
    print(json.dumps(evidence, indent=2))


if __name__ == '__main__':
    main()
