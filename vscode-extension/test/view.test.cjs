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
