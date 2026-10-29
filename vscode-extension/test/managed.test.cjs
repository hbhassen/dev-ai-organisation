const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const processes = require('node:child_process');

/** Loads the real gate with a minimal native trust surface and no workstation authentication. Exigences : DEVAI-169, DEVAI-177. */
function managed(api) {
  const original = Module._load;
  Module._load = function load(request, parent, main) { return request === 'vscode' ? api : original.call(this, request, parent, main); };
  try { delete require.cache[require.resolve('../dist/managed')]; return require('../dist/managed'); }
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
});
