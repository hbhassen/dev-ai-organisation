"""Wait for local services and create accounts once, without logging credentials. Exigences : DEVAI-167, DEVAI-168, DEVAI-177."""
import json
from pathlib import Path
import shutil
import ssl
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parent
LOCAL = ROOT / '.local'


def main():
    """Initialise les comptes GitLab du banc local. Exigences : DEVAI-167, DEVAI-168, DEVAI-177."""
    context = ssl.create_default_context(cafile=str(LOCAL / 'tls/ca.crt'))
    pending = {'GitLab': 'https://localhost:18443/users/sign_in', 'SonarQube': 'https://localhost:19443/api/system/status'}
    deadline = time.monotonic() + 1200
    while pending and time.monotonic() < deadline:
        for name, url in list(pending.items()):
            try:
                with urllib.request.urlopen(url, context=context, timeout=10) as response:
                    body = json.load(response) if name == 'SonarQube' else {}
                    if name == 'SonarQube' and body.get('status') != 'UP':
                        continue
                del pending[name]
                print(name + ' ready over verified HTTPS.', flush=True)
            except (OSError, ValueError):
                pass
        if pending:
            print('Waiting for ' + ', '.join(pending) + '...', flush=True)
            time.sleep(15)
    if pending:
        raise RuntimeError('Service readiness timeout: ' + ', '.join(pending))
    command = ['docker', 'compose', '-f', str(ROOT / 'compose.yaml')]
    if not (LOCAL / 'accounts.json').exists():
        shutil.copy2(ROOT / 'seed_accounts.rb', LOCAL / 'bootstrap/seed_accounts.rb')
        subprocess.run(command + ['exec', '-T', 'gitlab', 'gitlab-rails', 'runner', '/bootstrap/seed_accounts.rb'], check=True)
        subprocess.run(command + ['cp', 'gitlab:/tmp/ods-accounts.json', str(LOCAL / 'accounts.json')], check=True)
    print('Accounts ready; run provision.py after the Java engine has been built.', flush=True)


if __name__ == '__main__':
    main()
