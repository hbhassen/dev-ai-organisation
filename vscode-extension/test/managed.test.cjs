const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const processes = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');

/** Loads the real gate with a minimal native trust surface and no workstation authentication. Exigences : DEVAI-169, DEVAI-177. */
function managed(api, moduleName = 'managed') {
  const original = Module._load;
  Module._load = function load(request, parent, main) { return request === 'vscode' ? api : original.call(this, request, parent, main); };
  try { delete require.cache[require.resolve('../dist/managed')]; delete require.cache[require.resolve('../dist/' + moduleName)]; return require('../dist/' + moduleName); }
  finally { Module._load = original; }
}

test('DEVAI-169: Git read is mandatory, host-pinned and denied in untrusted workspaces', async () => {
  const original = processes.execFile, api = { workspace: { isTrusted: true } }, calls = [];
  const { GitAccess } = managed(api); const gate = new GitAccess();
  let refused = false, remote = 'git@gitlab.invalid:team/project.git';
  processes.execFile = (_exe, args, options, callback) => { calls.push(args); assert.equal(options.env.GIT_TERMINAL_PROMPT, '0'); callback(refused ? new Error('private error') : null, args[0] === 'remote' ? remote : ''); };
  try {
    assert.deepEqual(await gate.verify({ uri: { fsPath: '/synthetic' } }, 'https://gitlab.invalid'), { path: 'team/project', remote });
    assert.deepEqual(calls[1], ['ls-remote', '--', remote, 'HEAD']);
    refused = true; await assert.rejects(() => gate.verify({ uri: { fsPath: '/synthetic' } }, 'https://gitlab.invalid'), /Accès Git refusé/);
    refused = false; remote = 'git@other.invalid:team/project.git'; const count = calls.length;
    await assert.rejects(() => gate.verify({ uri: { fsPath: '/synthetic' } }, 'https://gitlab.invalid'), /GitLab administré/); assert.equal(calls.length, count + 1);
    api.workspace.isTrusted = false; const prior = calls.length;
    await assert.rejects(() => gate.verify({ uri: { fsPath: '/synthetic' } }, 'https://gitlab.invalid'), /Approuver/); assert.equal(calls.length, prior);
  } finally { processes.execFile = original; }
});

test('DEVAI-176: masks raw and Basic credentials including JSON-special characters', () => {
  const { redact } = managed({});
  const password = 'synthetic"secret\\value', auth = Buffer.from('service:' + password).toString('base64');
  const environment = { ODS_JFROG_USERNAME: 'service', ODS_JFROG_PASSWORD: password };
  const text = `secret=${password} Authorization: Basic ${auth}`;
  assert.equal(redact(text, environment), 'secret=<REDACTED> Authorization: Basic <REDACTED>');
  const roundtrip = JSON.parse(JSON.stringify({ message: text }), (_key, value) => typeof value === 'string' ? redact(value, environment) : value);
  assert.equal(roundtrip.message, redact(text, environment));
  for (const separator of [String.fromCharCode(92), String.fromCharCode(92).repeat(2), '/']) {
    const privatePath = ['C:', 'Users', 'synthetic-person', '.m2', 'agent.jar'].join(separator);
    assert.equal(redact('argLine=' + privatePath, environment), 'argLine=<USER_PATH>');
  }
});

test('DEVAI-168: closing during native secret access prevents a late gateway process', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ods-start-cancel-'));
  fs.mkdirSync(path.join(directory, 'runtime'));
  fs.writeFileSync(path.join(directory, 'runtime/ods-gateway.jar'), 'synthetic');
  fs.writeFileSync(path.join(directory, 'runtime/ods-gateway.jar.sha256'), crypto.createHash('sha256').update('synthetic').digest('hex'));
  let release;
  const key = new Promise(resolve => { release = resolve; });
  const { ManagedGateway } = managed({});
  const runtime = new ManagedGateway({ extensionPath: directory, globalStorageUri: { fsPath: directory }, secrets: { get: () => key } }, { profileId: 'test', environment: {} }, { uri: { toString: () => 'file:///synthetic' } }, 'team/project');
  try {
    const started = runtime.start(); runtime.dispose(); release('synthetic-key');
    await assert.rejects(started, /annulé/);
    assert.equal(runtime.child, undefined);
    await assert.rejects(runtime.start(), /arrêtée/);
  } finally {
    assert.ok(fs.realpathSync(directory).startsWith(fs.realpathSync(os.tmpdir()) + path.sep));
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

test('DEVAI-168/169: closing during Git verification cannot reconnect afterwards', async () => {
  const { GatewayClient } = managed({}, 'gateway');
  const client = new GatewayClient({});
  client.config = { environment: { ODS_GITLAB_URL: 'https://gitlab.invalid' } };
  let release;
  client.git.verify = () => new Promise(resolve => { release = resolve; });
  const pending = client.connect({}); client.dispose(); release({ path: 'team/project' });
  await assert.rejects(pending, /annulée/);
  assert.equal(client.runtime, undefined);
});
