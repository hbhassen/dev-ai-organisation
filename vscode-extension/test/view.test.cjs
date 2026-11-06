const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');

/** Loads the real native view against an isolated workspace API for deterministic revocation tests. Exigences : DEVAI-017, DEVAI-024. */
function viewWithClosedWorkspace(gateway) {
  gateway.disposed = false;
  gateway.dispose = () => { gateway.disposed = true; };
  const original = Module._load;
  Module._load = /** Substitutes only the native workspace surface while preserving real application imports. Exigences : DEVAI-135. */ function load(request, parent, main) { return request === 'vscode' ? { workspace: { workspaceFolders: [] } } : original.call(this, request, parent, main); };
  let OdsView;
  try { delete require.cache[require.resolve('../dist/view')]; ({ OdsView } = require('../dist/view')); }
  finally { Module._load = original; }
  const engine = { disposed: false, /** Records the owned engine shutdown before network effects. Exigences : DEVAI-024. */ dispose() { this.disposed = true; } };
  const view = new OdsView({ workspaceState: { get: () => undefined } }, engine, gateway);
  view.root = { uri: { toString: () => 'file:///test-project' } }; view.project = 1042; view.activeRun = 'run-1'; view.conversation = { id: 'run-1' }; view.grants.add('context'); view.reports.set('SONAR', {});
  return { view, engine };
}

test('DEVAI-017: closing the selected root revokes grants and stops local tools before remote cancellation', async () => {
  let state;
  const gateway = { /** Checks local revocation before the remote cancellation is submitted. Exigences : DEVAI-024. */ async request(path) { assert.equal(state.engine.disposed, true); assert.equal(state.view.project, undefined); assert.equal(path, '/api/v1/jobs/run-1/cancel'); } };
  state = viewWithClosedWorkspace(gateway);
  await state.view.workspaceChanged();
  assert.equal(state.view.grants.size, 0); assert.equal(state.view.reports.size, 0); assert.equal(state.view.root, undefined); assert.equal(state.view.stopped, true);
  assert.equal(gateway.disposed, true);
});

test('DEVAI-024: remote cancellation failure cannot retain local context or execution rights', async () => {
  const state = viewWithClosedWorkspace({ /** Simulates a network failure after local rights must already be gone. Exigences : DEVAI-024. */ async request() { throw new Error('network unavailable'); } });
  await state.view.workspaceChanged();
  assert.equal(state.engine.disposed, true); assert.equal(state.view.conversation, undefined); assert.equal(state.view.grants.size, 0); assert.equal(state.view.project, undefined);
});

test('DEVAI-169: removing origin stops an already owned gateway on refresh', async () => {
  const gateway = {};
  const { view, engine } = viewWithClosedWorkspace(gateway);
  view.selectRoot = async () => {};
  engine.call = async method => { assert.equal(method, 'project.inspect'); return { stacks: ['PYTHON'] }; };
  await view.refresh();
  assert.equal(gateway.disposed, true);
  assert.equal(view.project, undefined);
  assert.equal(view.capabilities, undefined);
});

/** Exercises the real chat controller with deterministic native dialogs and gateway job metadata. Exigences : DEVAI-065, DEVAI-135, DEVAI-192, DEVAI-193. */
function chatView(choice, tools = false) {
  const dialogs = [], requests = [], messages = [];
  const api = { workspace: { isTrusted: true }, window: { showWarningMessage: async (text, options, ...choices) => { dialogs.push({ text, choices }); return choice; } } };
  const original = Module._load;
  Module._load = function load(request, parent, main) { return request === 'vscode' ? api : original.call(this, request, parent, main); };
  let OdsView;
  try { delete require.cache[require.resolve('../dist/view')]; ({ OdsView } = require('../dist/view')); } finally { Module._load = original; }
  const gateway = { request: async (path, method, body) => { requests.push({ path, method, body }); return { jobId: 'job', consumed: 1 }; } };
  const view = new OdsView({ workspaceState: { update: async () => {} } }, {}, gateway);
  view.root = { uri: { toString: () => 'file:///fixture' } }; view.project = 1042;
  view.capabilities = { provider: 'OPENAI', model: 'selected-model', tools, rule: { contextMode: 'LOCAL_GOVERNED' } };
  view.prepareContext = async () => [];
  view.waitJob = async () => ({ status: 'SUCCEEDED', result: { provider: 'OPENAI', model: 'executed-model', message: { content: 'Réponse simulée' } } });
  view.post = (type, data) => messages.push({ type, ...data });
  return { view, dialogs, requests, messages };
}

const question = { mode: 'QUESTION', task: 'code-generation', message: 'Explique le projet' };
test('DEVAI-065/193: canceling empty context with disabled tools makes no model request and keeps the draft', async () => {
  const state = chatView(undefined); await state.view.chat(question);
  assert.equal(state.dialogs.length, 1); assert.match(state.dialogs[0].text, /OpenAI · selected-model/);
  assert.match(state.dialogs[0].text, /aucun fichier/); assert.equal(state.requests.length, 0);
  assert.equal(state.messages.some(m => m.type === 'accepted'), false);
});
test('DEVAI-065: selecting attachments from the empty-context notice defers sending until preview', async () => {
  const state = chatView('Ajouter des fichiers'); let attached = false;
  state.view.attach = async () => { attached = true; };
  await state.view.chat(question); assert.equal(attached, true); assert.equal(state.requests.length, 0);
});
test('DEVAI-192/193: explicit text-only sending retains actual job attribution instead of selected metadata', async () => {
  const state = chatView('Envoyer sans fichiers'); await state.view.chat(question);
  assert.equal(state.dialogs.length, 1); assert.deepEqual(state.requests[0].body.context, []);
  assert.deepEqual(state.messages.find(m => m.role === 'assistant'), { type: 'chat', role: 'assistant', text: 'Réponse simulée', provider: 'OPENAI', model: 'executed-model' });
  await state.view.chat(question); assert.equal(state.dialogs.length, 1); // Existing conversation retains its history.
});
test('DEVAI-065/195: a previewed file suffices when read tools are disabled without misleading tool consent', async () => {
  const state = chatView(undefined); state.view.prepareContext = async () => [{ path: 'pom.xml', content: '<project/>' }];
  await state.view.chat(question); assert.equal(state.dialogs.length, 0); assert.equal(state.requests[0].body.context[0].path, 'pom.xml');
});
test('DEVAI-192/195: enabled read tools retain consent addressed to the selected provider and model', async () => {
  const state = chatView('Autoriser cette action', true); await state.view.chat(question);
  assert.equal(state.dialogs.length, 1); assert.match(state.dialogs[0].text, /transmettre à OpenAI · selected-model/);
  assert.equal(state.requests[0].path, '/api/v1/ai/chat');
});
test('DEVAI-065/193: server-verified context does not advertise local read tools', async () => {
  const state = chatView(undefined, true); state.view.capabilities.rule.contextMode = 'SERVER_VERIFIED';
  await state.view.chat(question); assert.match(state.dialogs[0].text, /outils de lecture sont désactivés/); assert.equal(state.requests.length, 0);
});

test('DEVAI-199: changing model clears the old conversation and grants and sends only the native-approved alias', async () => {
  const state = chatView('Autoriser cette action', true);
  state.view.capabilities.models = [{ modelAlias: 'first', provider: 'MISTRAL', model: 'mistral-model', tools: true }, { modelAlias: 'second', provider: 'OPENAI', model: 'openai-model', tools: true }];
  state.view.applyCapabilities(state.view.capabilities, 'first'); state.view.conversation = { id: 'old-history', mode: 'QUESTION', task: 'code-generation', modelAlias: 'first' }; state.view.grants.add('edit');
  await state.view.selectModel('second'); assert.equal(state.view.conversation, undefined); assert.equal(state.view.grants.size, 0);
  await state.view.chat({ ...question, modelAlias: 'forged-alias' });
  assert.equal(state.requests[0].path, '/api/v1/ai/chat'); assert.equal(state.requests[0].body.modelAlias, 'second');
  assert.equal(state.view.conversation.modelAlias, 'second');
  await assert.rejects(() => state.view.selectModel('foreign'), /absent des choix/);
  state.view.activeRun = 'active'; await assert.rejects(() => state.view.selectModel('first'), /Arrêter la tâche/);
});
test('DEVAI-199: revoked selection requires an explicit replacement and never falls back silently', async () => {
  const state = chatView('Autoriser cette action', true);
  state.view.applyCapabilities({ rule: { aiEnabled: true }, modelAlias: 'default', models: [{ modelAlias: 'default', provider: 'OPENAI', model: 'approved', tools: true }] }, 'revoked');
  assert.equal(state.view.capabilities.selectionRequired, true);
  state.view.applyCapabilities(state.view.capabilities, state.view.selectedModel); assert.equal(state.view.capabilities.selectionRequired, true); assert.equal(state.view.selectedModel, 'revoked');
  await assert.rejects(() => state.view.chat(question), /sélectionner un modèle/); assert.equal(state.requests.length, 0);
  await state.view.selectModel('default'); assert.equal(state.view.capabilities.selectionRequired, false);
  await state.view.chat(question); assert.equal(state.requests[0].body.modelAlias, 'default');
});
test('DEVAI-199: the historical text-only profile rejects modification and agent before any request', async () => {
  const state = chatView('Envoyer sans fichiers', false);
  for (const mode of ['MODIFICATION', 'AGENT']) await assert.rejects(() => state.view.chat({ ...question, mode }), /outils nécessaires/);
  assert.equal(state.requests.length, 0); assert.equal(state.dialogs.length, 0);
});

for (const compatible of [true, false]) test(`DEVAI-199: legacy resume verifies persisted model provenance (${compatible})`, async () => {
  const state = chatView('Reprendre', true);
  state.view.capabilities.modelAlias = 'approved';
  const saved = { id: 'legacy', mode: 'QUESTION', task: 'code-generation' };
  state.view.context.workspaceState.get = () => saved;
  state.view.gateway.request = async path => { assert.equal(path, '/api/v1/jobs/legacy'); return { result: compatible ? { modelAlias: 'approved', provider: 'OPENAI', model: 'selected-model' } : {} }; };
  let resumed;
  state.view.chat = async input => { resumed = input; };
  if (compatible) { await state.view.handle({ type: 'resume' }); assert.equal(resumed.resume, true); assert.equal(saved.modelAlias, 'approved'); }
  else { await assert.rejects(() => state.view.handle({ type: 'resume' }), /Provenance/); assert.equal(resumed, undefined); }
});
