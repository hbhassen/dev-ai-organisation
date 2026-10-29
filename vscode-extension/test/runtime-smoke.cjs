const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const Module = require('node:module');
const processes = require('node:child_process');

/** Starts the real packaged gateway after a mocked developer Git read and verifies private HTTP access plus owned shutdown. Exigences : DEVAI-168, DEVAI-169, DEVAI-177. */
async function main() {
  const extensionPath = path.resolve(process.argv[2]);
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'ods-runtime-smoke-'));
  const config = JSON.parse(fs.readFileSync(path.join(extensionPath, 'runtime/deployment.json'), 'utf8'));
  const secrets = new Map();
  const root = { uri: { fsPath: temporary, toString: () => 'file:///synthetic-runtime-test' } };
  const api = { workspace: { isTrusted: true, getConfiguration: () => ({ get: (_key, fallback) => fallback }) } };
  const original = Module._load, originalExec = processes.execFile;
  let gitAllowed = false, gitReads = 0, gateway;
  processes.execFile = (command, args, options, callback) => {
    assert.equal(command, 'git');
    if (args[0] === 'remote') return callback(null, `git@${new URL(config.environment.ODS_GITLAB_URL).hostname}:synthetic/project.git`);
    assert.equal(args[0], 'ls-remote'); gitReads++;
    callback(gitAllowed ? null : new Error('synthetic access denial'), '');
  };
  Module._load = function load(request, parent, isMain) { return request === 'vscode' ? api : original.call(this, request, parent, isMain); };
  try {
    const { GatewayClient } = require(path.join(extensionPath, 'dist/gateway.js'));
    gateway = new GatewayClient({ extensionPath, globalStorageUri: { fsPath: temporary }, secrets: { get: async key => secrets.get(key), store: async (key, value) => secrets.set(key, value) } });
    await assert.rejects(() => gateway.connect(root), /Accès Git refusé/);
    assert.equal(gateway.runtime, undefined, 'Git denial must precede process creation');
    gitAllowed = true; await gateway.connect(root); assert.equal(gitReads, 2);
    const child = gateway.runtime.child, base = gateway.base();
    const denied = await fetch(new URL('/api/v1/prompts', base)); assert.ok([401, 403].includes(denied.status));
    const prompts = await gateway.request('/api/v1/prompts'); assert.ok(Array.isArray(prompts) && prompts.length >= 3);
    assert.ok(secrets.has('ods.localDataKey'), 'Persistent encryption key stored natively');
    const stopped = new Promise((resolve, reject) => { const timer = setTimeout(() => reject(new Error('Owned gateway did not stop')), 15000); child.once('exit', () => { clearTimeout(timer); resolve(); }); });
    gitAllowed = false; await assert.rejects(() => gateway.connect(root), /Accès Git refusé/); await stopped;
    assert.equal(gateway.runtime, undefined);
    console.log('Packaged gateway: Git denial before startup, authenticated loopback, persistent key and owned shutdown SUCCESS');
  } finally {
    gateway?.dispose(); Module._load = original; processes.execFile = originalExec;
    // Keep shutdown and filesystem removal separate; remove only the explicitly created temporary directory.
    assert.ok(fs.realpathSync(temporary).startsWith(fs.realpathSync(os.tmpdir()) + path.sep));
    assert.ok(path.basename(temporary).startsWith('ods-runtime-smoke-'));
    fs.rmSync(temporary, { recursive: true, force: true, maxRetries: 20, retryDelay: 250 });
  }
}
main().catch(() => { console.error('Packaged runtime smoke failed; no provider response or credential is logged.'); process.exitCode = 1; });
