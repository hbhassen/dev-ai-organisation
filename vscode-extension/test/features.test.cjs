const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');

/** Builds native URI values for isolated host-boundary tests without accessing a real workstation. Exigences : DEVAI-017, DEVAI-135. */
function uri(value) { return { fsPath: value, toString: () => value }; }
/** Loads the actual controller against explicit native API test doubles. Exigences : DEVAI-042, DEVAI-068, DEVAI-135. */
function createView(api, engine, gateway = {}) {
  gateway.dispose ??= () => {};
  const original = Module._load;
  Module._load = /** Injects only the VS Code API; application methods remain unchanged. Exigences : DEVAI-135. */ function load(request, parent, main) { return request === 'vscode' ? api : original.call(this, request, parent, main); };
  let View; try { delete require.cache[require.resolve('../dist/view')]; View = require('../dist/view').OdsView; } finally { Module._load = original; }
  const stored = new Map();
  const view = new View({ workspaceState: { get: key => stored.get(key), update: async (key, value) => stored.set(key, value) } }, engine, gateway);
  view.root = { uri: uri('/project') }; view.project = 1042; view.inspection = { commit: 'revision', branch: 'feat/example' };
  return view;
}

test('DEVAI-042/065: multifile preview sends exactly the approved content and no context after refusal', async () => {
  let preview, approve = true;
  const api = { Uri: { joinPath: (base, path) => uri(base.fsPath + '/' + path) }, workspace: { textDocuments: [], openTextDocument: async data => { preview = data.content; return data; } }, window: { showTextDocument: async () => {}, showInformationMessage: async () => approve ? 'Transmettre' : undefined } };
  const view = createView(api, { call: async (method, args) => ({ content: `contents of ${args.path}` }) });
  view.attached = ['pom.xml', 'src/Main.java'];
  assert.deepEqual(await view.prepareContext(false), [{ path: 'pom.xml', content: 'contents of pom.xml' }, { path: 'src/Main.java', content: 'contents of src/Main.java' }]);
  assert.match(preview, /contents of pom.xml/); assert.match(preview, /contents of src\/Main.java/);
  approve = false; assert.equal(await view.prepareContext(false), undefined);
});

test('DEVAI-041: server-verified attachments contain commit references and reject dirty buffers', async () => {
  const documents = [];
  const api = { Uri: { joinPath: (base, path) => uri(base.fsPath + '/' + path) }, workspace: { textDocuments: documents, openTextDocument: async data => data }, window: { showTextDocument: async () => {}, showInformationMessage: async () => 'Transmettre' } };
  const view = createView(api, { call: async () => ({ content: 'local content' }) }); view.attached = ['pom.xml']; view.capabilities = { rule: { contextMode: 'SERVER_VERIFIED' } };
  assert.deepEqual(await view.prepareContext(false), [{ path: 'pom.xml', commit: 'revision' }]);
  documents.push({ uri: uri('/project/pom.xml'), isDirty: true });
  await assert.rejects(() => view.prepareContext(false), /Enregistrer et publier/);
});

test('DEVAI-068: only selected reviewed files are applied using a recomputed proposal hash', async () => {
  let applied; const proposals = [];
  const api = { Uri: { joinPath: (base, path) => uri(base.fsPath + '/' + path), parse: uri }, workspace: { textDocuments: [] }, commands: { executeCommand: async () => {} }, window: { showQuickPick: async values => [values[0]], showWarningMessage: async () => 'Autoriser cette action' } };
  const engine = { call: async (method, args) => { if (method === 'changes.propose') { proposals.push(args); return { changes: args.changes, planHash: `hash-${args.changes.length}` }; } if (method === 'changes.apply') { applied = args; return { applied: 1, backup: 'checkpoint' }; } throw new Error(method); } };
  const view = createView(api, engine);
  const changes = [{ path: 'a.java', baseHash: 'MISSING', content: 'A' }, { path: 'b.java', baseHash: 'MISSING', content: 'B' }];
  const result = await view.tool('changes_propose', { changes });
  assert.equal(proposals.length, 2); assert.equal(applied.planHash, 'hash-1'); assert.deepEqual(applied.changes, [changes[0]]); assert.deepEqual(result.omitted, ['b.java']);
});

test('DEVAI-072: changing plan scope revokes prior command and edit grants', async () => {
  const view = createView({}, {}); view.grants.add('edit'); view.grants.add('command:test');
  await view.tool('plan_update', { steps: [{ title: 'Implement requirement', status: 'pending' }] }); assert.equal(view.grants.size, 0);
  view.grants.add('command:test'); await view.tool('plan_update', { steps: [{ title: 'Implement requirement', status: 'done' }] }); assert.equal(view.grants.size, 1);
  await view.tool('plan_update', { steps: [{ title: 'Publish remotely', status: 'pending' }] }); assert.equal(view.grants.size, 0);
});

test('DEVAI-113: MR creation prefills a matching draft and requires reviewed publication', async () => {
  const inputs = []; let published;
  const api = { workspace: { isTrusted: true }, window: { showInputBox: async options => { inputs.push(options); return options.value || 'Reviewed title'; }, showWarningMessage: async () => 'Publier' } };
  const view = createView(api, { call: async () => ({ commit: 'revision', branch: 'feat/example' }) }, { request: async (route, method, body) => { published = body; return {}; } });
  view.mrDraft = { text: 'Draft from the governed prompt', commit: 'revision', branch: 'feat/example' }; await view.mr();
  assert.equal(inputs[2].value, 'Draft from the governed prompt'); assert.equal(published.description, 'Draft from the governed prompt');
});

test('DEVAI-024/077: closing a root during job submission cancels the accepted job and clears native state', async () => {
  let resolveJob, submitted;
  const calls = [];
  const ready = new Promise(resolve => { submitted = resolve; });
  const gateway = { request: async route => { calls.push(route); if (route === '/api/v1/ai/chat') { submitted(); return new Promise(resolve => { resolveJob = resolve; }); } return {}; } };
  const view = createView({ workspace: { isTrusted: true, workspaceFolders: [] } }, { dispose: () => {} }, gateway);
  view.prepareContext = async () => []; view.approval = async () => true;
  const running = view.chat({ mode: 'QUESTION', task: 'code-review', message: 'Inspect' });
  await ready; await assert.rejects(() => view.chat({ mode: 'QUESTION', message: 'Duplicate' }), /déjà active/);
  await view.workspaceChanged(); resolveJob({ jobId: 'pending-job' }); await running;
  assert.deepEqual(calls, ['/api/v1/ai/chat', '/api/v1/jobs/pending-job/cancel']); assert.equal(view.activeRun, undefined); assert.equal(view.conversation, undefined); assert.equal(view.chatBusy, false);
});

test('DEVAI-024: stopping during context consent never submits a model request', async () => {
  let calls = 0;
  const view = createView({ workspace: { isTrusted: true } }, {}, { request: async () => { calls++; return {}; } });
  view.prepareContext = async () => []; view.approval = async () => { view.stopped = true; return true; };
  await assert.rejects(() => view.chat({ mode: 'QUESTION', task: 'code-review', message: 'Inspect' }), /arrêtée avant envoi/);
  assert.equal(calls, 0); assert.equal(view.chatBusy, false);
});
