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
    parser.add_argument('--profile', choices=['Test', 'Test1'], default='Test')
    args = parser.parse_args()
    exe = LOCAL / 'vscode-insiders/Code - Insiders.exe' if args.profile == 'Test1' else executable()
    data = LOCAL / ('test1-data' if args.profile == 'Test1' else 'vscode-data')
    extensions = LOCAL / ('test1-extensions' if args.profile == 'Test1' else 'extensions')
    common = ['--user-data-dir', str(data), '--extensions-dir', str(extensions), '--profile', args.profile]
    if args.action == 'configure':
        user = data / 'User'
        destinations = [user, *list((user / 'profiles').glob('*'))]
        for folder in destinations:
            if not folder.is_dir():
                continue
            file = folder / 'settings.json'
            settings = json.loads(file.read_text()) if file.exists() else {}
            settings.update({'ods.javaPath': str(LOCAL / 'jdk/bin/java.exe'), 'workbench.startupEditor': 'none',
                'window.title': '${rootName} - ' + args.profile + ' - ${appName}', 'update.mode': 'none', 'extensions.autoUpdate': False,
                'terminal.integrated.env.windows': {'JAVA_HOME': str(LOCAL / 'jdk'), 'PATH': str(LOCAL / 'jdk/bin') + ';${env:PATH}'}})
            file.write_text(json.dumps(settings, indent=2))
        print('Isolated VS Code profile configured with private Java trust.')
    elif args.action in ('install', 'list'):
        options = ['--list-extensions', '--show-versions'] if args.action == 'list' else ['--install-extension', str(ROOT.parent / '.artifacts/ods-docker-manual-0.5.0.vsix'), '--force']
        cli = exe.parent / 'bin' / ('code-insiders.cmd' if args.profile == 'Test1' else 'code.cmd')
        subprocess.run([str(cli), *common, *options], check=True)
    else:
        env = dict(os.environ)
        env.pop('ELECTRON_RUN_AS_NODE', None)
        env['JAVA_HOME'] = str(LOCAL / 'jdk')
        env['PATH'] = str(LOCAL / 'jdk/bin') + os.pathsep + env.get('PATH', '')
        with (LOCAL / 'vscode-open.log').open('ab') as output:
            workspace = 'test1-corrections' if args.profile == 'Test1' else 'demo-allowed'
            child = subprocess.Popen([str(exe), *common, '--disable-workspace-trust', '--reuse-window', str(LOCAL / 'workspaces' / workspace)],
                env=env, stdout=output, stderr=output)
        print('VS Code ' + args.profile + ' launch requested, PID=' + str(child.pid))


if __name__ == '__main__':
    main()
