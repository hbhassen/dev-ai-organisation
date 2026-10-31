"""Prepare private local configuration and TLS without changing application sources. Exigences : DEVAI-167, DEVAI-168, DEVAI-177."""
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent
LOCAL = ROOT / '.local'


def run(*args):
    """Execute la commande du banc avec controle de son resultat. Exigences : DEVAI-167, DEVAI-168, DEVAI-177."""
    subprocess.run([str(a) for a in args], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def main():
    """Prepare les fichiers et prerequis du banc Docker. Exigences : DEVAI-167, DEVAI-168, DEVAI-177."""
    LOCAL.mkdir(exist_ok=True)
    tls = LOCAL / 'tls'
    tls.mkdir(exist_ok=True)
    bootstrap = LOCAL / 'bootstrap'
    bootstrap.mkdir(exist_ok=True)
    if not (LOCAL / 'root-password.txt').exists():
        (LOCAL / 'root-password.txt').write_text(secrets.token_urlsafe(24))
    if not (bootstrap / 'accounts.json').exists():
        (bootstrap / 'accounts.json').write_text(json.dumps({'service_password': secrets.token_urlsafe(24),
            'user_password': os.environ.get('ODS_TEST_USER_PASSWORD') or secrets.token_urlsafe(18)}))
    shutil.copy2(ROOT / 'seed_accounts.rb', bootstrap / 'seed_accounts.rb')
    if not (LOCAL / 'mock.env').exists():
        (LOCAL / 'mock.env').write_text('MOCK_XRAY_USER=serviceExtebtion\nMOCK_XRAY_PASSWORD=' + secrets.token_urlsafe(24) +
            '\nMOCK_XRAY_TOKEN=' + secrets.token_urlsafe(32) + '\nMOCK_XRAY_WATCH=manual-test-watch\n')
    openssl = shutil.which('openssl')
    if not openssl:
        raise RuntimeError('OpenSSL must be installed on PATH')
    if not (tls / 'ca.crt').exists():
        run(openssl, 'req', '-x509', '-newkey', 'rsa:3072', '-nodes', '-days', '365', '-sha256',
            '-subj', '/CN=ODS Manual Test Local CA', '-keyout', tls / 'ca.key', '-out', tls / 'ca.crt',
            '-addext', 'basicConstraints=critical,CA:TRUE', '-addext', 'keyUsage=critical,keyCertSign,cRLSign')
    if not (tls / 'server.crt').exists():
        run(openssl, 'req', '-new', '-newkey', 'rsa:2048', '-nodes', '-subj', '/CN=localhost',
            '-keyout', tls / 'server.key', '-out', tls / 'server.csr')
        (tls / 'server.ext').write_text('subjectAltName=DNS:localhost,IP:127.0.0.1\nbasicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n')
        run(openssl, 'x509', '-req', '-in', tls / 'server.csr', '-CA', tls / 'ca.crt', '-CAkey', tls / 'ca.key',
            '-CAcreateserial', '-days', '365', '-sha256', '-extfile', tls / 'server.ext', '-out', tls / 'server.crt')
    # A private JDK copy gives the gateway a trusted CA without altering the installed JDK.
    java = Path(shutil.which('java')).resolve()
    private_jdk = LOCAL / 'jdk'
    if not private_jdk.exists():
        shutil.copytree(java.parent.parent, private_jdk)
    run(private_jdk / 'bin/keytool.exe', '-importcert', '-noprompt', '-alias', 'ods-manual-test',
        '-file', tls / 'ca.crt', '-keystore', private_jdk / 'lib/security/cacerts', '-storepass', 'changeit') if not (LOCAL / 'jdk-trusted').exists() else None
    (LOCAL / 'jdk-trusted').touch()
    print('Private configuration, TLS and isolated Java trust prepared.')


if __name__ == '__main__':
    main()
