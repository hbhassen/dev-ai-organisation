"""Install/configure/open only the isolated VS Code Test profile."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent
LOCAL = ROOT / '.local'


def executable():
    selected = LOCAL / 'vscode-executable.txt'
    if selected.exists():
        result = Path(selected.read_text().strip())
    else:
        command = shutil.which('code.cmd')
        if not command:
            raise RuntimeError('VS Code CLI not found')
        result = Path(command).parent.parent / 'Code.exe'
    if not result.is_file():
        raise RuntimeError('Selected VS Code executable is missing')
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['open', 'install', 'configure', 'list'])
    args = parser.parse_args()
    exe = executable()
    common = ['--user-data-dir', str(LOCAL / 'vscode-data'), '--extensions-dir', str(LOCAL / 'extensions'), '--profile', 'Test']
    if args.action == 'configure':
        user = LOCAL / 'vscode-data/User'
        destinations = [user, *list((user / 'profiles').glob('*'))]
        for folder in destinations:
            if not folder.is_dir():
                continue
            file = folder / 'settings.json'
            settings = json.loads(file.read_text()) if file.exists() else {}
            settings.update({'ods.javaPath': str(LOCAL / 'jdk/bin/java.exe'), 'workbench.startupEditor': 'none',
                'window.title': '${rootName} - Test - ${appName}', 'update.mode': 'none', 'extensions.autoUpdate': False,
                'terminal.integrated.env.windows': {'JAVA_HOME': str(LOCAL / 'jdk'), 'PATH': str(LOCAL / 'jdk/bin') + ';${env:PATH}'}})
            file.write_text(json.dumps(settings, indent=2))
        print('Isolated VS Code profile configured with private Java trust.')
    elif args.action in ('install', 'list'):
        options = ['--list-extensions', '--show-versions'] if args.action == 'list' else ['--install-extension', str(ROOT.parent / '.artifacts/ods-docker-manual-0.3.0.vsix'), '--force']
        subprocess.run([str(exe.parent / 'bin/code.cmd'), *common, *options], check=True)
    else:
        env = dict(os.environ)
        env.pop('ELECTRON_RUN_AS_NODE', None)
        env['JAVA_HOME'] = str(LOCAL / 'jdk')
        env['PATH'] = str(LOCAL / 'jdk/bin') + os.pathsep + env.get('PATH', '')
        with (LOCAL / 'vscode-open.log').open('ab') as output:
            child = subprocess.Popen([str(exe), *common, '--disable-workspace-trust', '--reuse-window', str(LOCAL / 'workspaces/demo-allowed')],
                env=env, stdout=output, stderr=output)
        print('VS Code Test launch requested, PID=' + str(child.pid))


if __name__ == '__main__':
    main()
