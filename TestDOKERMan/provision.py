"""Provision isolated test projects, signed governance and Sonar through local APIs only."""
import base64
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import secrets
import shutil
import ssl
import subprocess
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent
LOCAL = ROOT / '.local'
GITLAB = 'https://localhost:18443'
SONAR = 'https://localhost:19443'


def api(base, route, token=None, data=None, basic=None, method=None):
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if basic:
        headers['Authorization'] = 'Basic ' + base64.b64encode(basic.encode()).decode()
    body = None
    if data is not None:
        if base == SONAR:
            body = urllib.parse.urlencode(data).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded'
        else:
            body = json.dumps(data).encode()
            headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(base + route, body, headers, method=method)
    context = ssl.create_default_context(cafile=str(LOCAL / 'tls/ca.crt'))
    try:
        with urllib.request.urlopen(req, context=context, timeout=90) as response:
            payload = response.read()
            return json.loads(payload) if payload else {}
    except urllib.error.HTTPError as error:
        # No provider bodies or credentials in terminal output.
        raise RuntimeError(f'Local API {route.split("?")[0]} failed: HTTP {error.code}') from None


def gitlab(route, data=None):
    accounts = json.loads((LOCAL / 'accounts.json').read_text())
    return api(GITLAB, '/api/v4/' + route, accounts['root']['token'], data)


def seed_project(project, files):
    existing = gitlab(f'projects/{project}/repository/tree')
    if any(item['path'] == 'pom.xml' for item in existing):
        return
    gitlab(f'projects/{project}/repository/commits', {'branch': 'main', 'commit_message': 'test: seed manual Java fixture',
        'actions': [{'action': 'create', 'file_path': name, 'content': content} for name, content in files.items()]})


def publish_policy(state):
    engine = ROOT.parent / 'ods-java-core/target/ods-java-core-0.3.1-engine.jar'
    java = LOCAL / 'jdk/bin/java.exe'
    keys = LOCAL / 'governance'
    keys.mkdir(exist_ok=True)
    command = [str(java), '-cp', str(engine), 'org.organisation.ods.security.GovernanceMain']
    if not (keys / 'signing.key').exists():
        subprocess.run(command + ['keygen', str(keys)], check=True)
    now = datetime.now(timezone.utc)
    enabled = {'projectId': state['demo-allowed'], 'aiEnabled': True,
        'allowedActions': ['CHAT', 'CODE_GENERATION', 'SONAR_REMEDIATION', 'XRAY_REMEDIATION', 'MR_DESCRIPTION', 'AGENT_RUN', 'CHANGELOG', 'RELEASE_NOTES'],
        'modelAlias': 'manual-coding', 'monthlyTokenQuota': 1000000, 'monthlyUserTokenQuota': 250000,
        'allowedAgents': ['implement-requirement'], 'contextMode': 'LOCAL_GOVERNED', 'allowFreeText': True}
    policy = {'schemaVersion': 1, 'policyVersion': int(now.timestamp()), 'issuedAt': now.isoformat(),
        'expiresAt': (now + timedelta(hours=23, minutes=59)).isoformat(), 'gitlabInstance': GITLAB,
        'defaultEffect': 'DENY', 'allowedProviders': ['MISTRAL'],
        'models': [{'alias': 'manual-coding', 'provider': 'MISTRAL', 'model': state['model'], 'tools': True}],
        'projects': [enabled, {'projectId': state['demo-denied'], 'aiEnabled': False, 'allowedActions': [], 'monthlyTokenQuota': 0}]}
    document = keys / 'ai-project-policy.json'
    document.write_text(json.dumps(policy, indent=2), encoding='utf8')
    subprocess.run(command + ['sign', str(document), str(keys / 'signing.key')], check=True)
    existing = {entry['path'] for entry in gitlab(f"projects/{state['governance']}/repository/tree")}
    actions = [{'action': 'update' if name in existing else 'create', 'file_path': name,
                'content': (keys / name).read_bytes().decode('utf8')} for name in ('ai-project-policy.json', 'ai-project-policy.sig')]
    gitlab(f"projects/{state['governance']}/repository/commits", {'branch': 'main',
        'commit_message': 'test: renew signed AI policy for 24 hours', 'actions': actions})
    for name in ('ai-project-policy.json', 'ai-project-policy.sig'):
        stored = gitlab(f"projects/{state['governance']}/repository/files/{name}?ref=main")
        if base64.b64decode(stored['content']) != (keys / name).read_bytes():
            raise RuntimeError('Published governance bytes differ from the signed document')
    print('Signed governance published: allowed and denied projects, validity under 24 hours.')


def main():
    accounts = json.loads((LOCAL / 'accounts.json').read_text())
    state_file = LOCAL / 'state.json'
    state = json.loads(state_file.read_text()) if state_file.exists() else {}
    state['model'] = 'mistral-medium-3-5'
    groups = gitlab('groups?search=ods-manual')
    group = next((g for g in groups if g['path'] == 'ods-manual'), None)
    if group is None:
        group = gitlab('groups', {'name': 'ODS Manual Tests', 'path': 'ods-manual', 'visibility': 'private'})
    for name in ('governance', 'demo-allowed', 'demo-denied'):
        if name not in state:
            projects = gitlab('groups/' + str(group['id']) + '/projects')
            project = next((p for p in projects if p['path'] == name), None)
            if project is None:
                project = gitlab('projects', {'name': name, 'namespace_id': group['id'], 'visibility': 'private',
                    'initialize_with_readme': True, 'default_branch': 'main'})
            state[name] = project['id']
            state_file.write_text(json.dumps(state, indent=2))
        project_id = state[name]
        members = gitlab(f'projects/{project_id}/members')
        for username, level in [('serviceExtebtion', 20), ('user1', 20 if name == 'governance' else 40)]:
            if not any(m['id'] == accounts[username]['id'] for m in members):
                gitlab(f'projects/{project_id}/members', {'user_id': accounts[username]['id'], 'access_level': level})
    fixture = ROOT / 'demo'
    files = {p.relative_to(fixture).as_posix(): p.read_text(encoding='utf8') for p in fixture.rglob('*') if p.is_file()}
    for name in ('demo-allowed', 'demo-denied'):
        seed_project(state[name], files)
        branches = gitlab(f"projects/{state[name]}/repository/branches")
        if not any(branch['name'] == 'develop' for branch in branches):
            gitlab(f"projects/{state[name]}/repository/branches", {'branch': 'develop', 'ref': 'main'})
    publish_policy(state)
    # Configure Sonar idempotently; never print token responses.
    sonar_file = LOCAL / 'sonar.json'
    if sonar_file.exists():
        sonar = json.loads(sonar_file.read_text())
    else:
        sonar = {'password': secrets.token_urlsafe(24)}
        api(SONAR, '/api/users/change_password', basic='admin:admin', data={'login': 'admin', 'previousPassword': 'admin', 'password': sonar['password']})
        sonar_file.write_text(json.dumps(sonar))
    auth = 'admin:' + sonar['password']
    if 'token' not in sonar:
        sonar['token'] = api(SONAR, '/api/user_tokens/generate', basic=auth, data={'name': 'ods-manual-test'})['token']
        sonar_file.write_text(json.dumps(sonar))
    for name in ('demo-allowed', 'demo-denied'):
        found = api(SONAR, '/api/projects/search?projects=' + name, basic=auth)
        if not found['components']:
            api(SONAR, '/api/projects/create', basic=auth, data={'project': name, 'name': name, 'visibility': 'private'})
    profile = LOCAL / 'profile'
    profile.mkdir(exist_ok=True)
    keyfile = LOCAL / 'mistral-api-key.txt'
    env = dict(line.split('=', 1) for line in (LOCAL / 'mock.env').read_text().splitlines() if '=' in line)
    values = {'ODS_LLM_TRANSPORT': 'VIBE_CLI', 'ODS_MISTRAL_URL': 'https://api.mistral.ai',
        'ODS_VIBE_URL': 'https://api.mistral.ai/v1', 'ODS_MISTRAL_MODEL': state['model'], 'ODS_MODEL_ALIAS': 'manual-coding',
        'ODS_VIBE_AUTH': 'API_KEY', 'ODS_MISTRAL_API_KEY': keyfile.read_text().strip() if keyfile.exists() else '<MISTRAL_VIBE_API_KEY>',
        'ODS_GITLAB_URL': GITLAB, 'ODS_GOVERNANCE_PROJECT_ID': str(state['governance']), 'ODS_GOVERNANCE_REF': 'main',
        'ODS_GOVERNANCE_TOKEN': accounts['serviceExtebtion']['token'],
        'ODS_POLICY_PUBLIC_KEY': (LOCAL / 'governance/public-key.txt').read_text().strip(),
        'ODS_SONAR_URL': SONAR, 'ODS_SONAR_USERNAME': 'admin', 'ODS_SONAR_TOKEN': sonar['token'],
        'ODS_JFROG_URL': 'https://localhost:18083', 'ODS_JFROG_AUTH': 'BASIC', 'ODS_JFROG_USERNAME': env['MOCK_XRAY_USER'],
        'ODS_JFROG_PASSWORD': env['MOCK_XRAY_PASSWORD'], 'ODS_JFROG_WATCH': env['MOCK_XRAY_WATCH']}
    (profile / 'gateway.env').write_text('\n'.join(k + '=' + v for k, v in values.items()) + '\n')
    (profile / 'organisation.json').write_text(json.dumps({'schemaVersion': 1, 'id': 'ods-docker-manual',
        'extension': {'name': 'ods-docker-manual', 'publisher': 'ods-local', 'displayName': 'ODS Docker Manual Test'},
        'gatewayUrl': 'http://127.0.0.1:8080', 'envFiles': ['gateway.env'], 'environment': {},
        'files': [{'source': 'application-organisation.properties', 'target': 'application-organisation.properties'}]}, indent=2))
    (profile / 'application-organisation.properties').write_text('\n'.join(f'ods.sonar-project-{state[name]}={name}' for name in ('demo-allowed', 'demo-denied')) + '\n')
    # Developer Git credential stays scoped to the dedicated local test origin in GCM.
    credential = f"protocol=https\nhost=localhost:18443\nusername=user1\npassword={accounts['user1']['token']}\n\n"
    subprocess.run(['git', '-c', 'credential.helper=', '-c', 'credential.helper=manager', 'credential', 'approve'], input=credential, text=True, check=True)
    workspaces = LOCAL / 'workspaces'
    workspaces.mkdir(exist_ok=True)
    for name in ('demo-allowed', 'demo-denied'):
        target = workspaces / name
        if not (target / '.git').exists():
            subprocess.run(['git', '-c', 'credential.helper=', '-c', 'credential.helper=manager', '-c', 'http.sslBackend=openssl', '-c', 'http.sslCAInfo=' + str(LOCAL / 'tls/ca.crt'),
                'clone', f'{GITLAB}/ods-manual/{name}.git', str(target)], check=True)
            subprocess.run(['git', '-C', str(target), 'switch', 'develop'], check=True)
        for key, value in {'http.sslBackend': 'openssl', 'http.sslCAInfo': str(LOCAL / 'tls/ca.crt'),
            'user.name': 'user1', 'user.email': 'user1@manual.test'}.items():
            subprocess.run(['git', '-C', str(target), 'config', key, value], check=True)
        subprocess.run(['git', '-C', str(target), 'config', '--local', '--replace-all', 'credential.helper', ''], check=True)
        subprocess.run(['git', '-C', str(target), 'config', '--local', '--add', 'credential.helper', 'manager'], check=True)
        mvn = target / '.mvn'
        mvn.mkdir(exist_ok=True)
        (mvn / 'jvm.config').write_text('-Djavax.net.ssl.trustStore=../../jdk/lib/security/cacerts\n-Djavax.net.ssl.trustStorePassword=changeit\n')
        (mvn / 'maven.config').write_text('-Dsonar.scanner.skipJreProvisioning=true\n"-Dsonar.scanner.javaExePath=' + str(LOCAL / 'jdk/bin/java.exe') + '"\n')
    print('Local GitLab, Sonar, private organisation profile and developer workspaces provisioned.')


if __name__ == '__main__':
    main()
