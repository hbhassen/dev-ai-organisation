"""Stage current compiled code against the private runtime and open only the Test1 profile. Exigences : DEVAI-167, DEVAI-168, DEVAI-177."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
from profile_test import immutable_library

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
LOCAL = HERE / '.local'
sys.path.insert(0, str(ROOT / 'tools'))
from runtime_bundle import extended, inventory


def main():
    """Preserve installed Test versions while staging a disposable extension development host. Exigences : DEVAI-167, DEVAI-168, DEVAI-177."""
    source = Path((LOCAL / 'stage-path.txt').read_text().strip())
    stage = ROOT / '.artifacts' / ('test1-stage-' + uuid.uuid4().hex[:12])
    shutil.copytree(extended(source), extended(stage), ignore=shutil.ignore_patterns('vibe', '__pycache__', '*.pyc'))
    shutil.copytree(extended(source / 'runtime/vibe'), extended(stage / 'runtime/vibe'), copy_function=immutable_library, ignore=shutil.ignore_patterns('__pycache__', '*.pyc'))
    for directory in ('dist', 'media'):
        shutil.copytree(ROOT / 'vscode-extension' / directory, stage / directory, dirs_exist_ok=True)
    version = json.loads((ROOT / 'vscode-extension/package.json').read_text())['version']
    manifest = json.loads((stage / 'package.json').read_text())
    manifest['version'] = version
    (stage / 'package.json').write_text(json.dumps(manifest, indent=2))
    for source_file, target in [(ROOT / f'ods-java-core/target/ods-java-core-{version}-engine.jar', stage / 'engine/ods-java-core.jar'), (ROOT / f'ods-gateway/target/ods-gateway-{version}.jar', stage / 'runtime/ods-gateway.jar')]:
        shutil.copy2(source_file, target)
        target.with_name(target.name + '.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest())
    shutil.copy2(ROOT / 'scripts/local_analysis.py', stage / 'runtime/local_analysis.py')
    inventory(stage / 'runtime')
    (LOCAL / 'test1-stage.txt').write_text(str(stage))
    user = LOCAL / 'test1-data/User'
    user.mkdir(parents=True, exist_ok=True)
    settings = {'ods.javaPath': str(LOCAL / 'jdk/bin/java.exe'), 'workbench.startupEditor': 'none', 'window.title': '${rootName} - Test1 - ${appName}', 'update.mode': 'none', 'extensions.autoUpdate': False, 'workbench.colorTheme': 'Default Light Modern'}
    for folder in (user, *list((user / 'profiles').glob('*'))):
        file = folder / 'settings.json'
        existing = json.loads(file.read_text()) if file.exists() else {}
        file.write_text(json.dumps(existing | settings, indent=2))
    environment = dict(os.environ)
    environment.pop('ELECTRON_RUN_AS_NODE', None)
    environment['JAVA_HOME'] = str(LOCAL / 'jdk')
    environment['PATH'] = str(LOCAL / 'jdk/bin') + os.pathsep + environment.get('PATH', '')
    executable = LOCAL / 'vscode-insiders/Code - Insiders.exe'
    args = [str(executable), '--user-data-dir', str(LOCAL / 'test1-data'), '--extensions-dir', str(LOCAL / 'test1-extensions'), '--profile', 'Test1', '--new-window', '--extensionDevelopmentPath', str(stage), str(LOCAL / 'workspaces/test1-corrections')]
    with (LOCAL / 'test1-launch.log').open('w', encoding='utf8') as log:
        subprocess.Popen(args, env=environment, stdout=log, stderr=subprocess.STDOUT)
    print('Test1 development host launched with current compiled code and the existing private runtime.')


if __name__ == '__main__':
    main()
