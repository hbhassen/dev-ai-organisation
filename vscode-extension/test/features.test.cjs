const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');

/** Builds native URI values for isolated host-boundary tests without accessing a real workstation. Exigences : DEVAI-017, DEVAI-135. */
function uri(value) { return { fsPath: value, toString: () => value }; }
/** Loads the actual controller against explicit native API test doubles. Exigences : DEVAI-042, DEVAI-068, DEVAI-135. */
function createView(api, engine, gateway = {}) {
  gateway.dispose ??= () => {};
  gateway.safe ??= text => text;
  const original = Module._load;
  Module._load = /** Injects only the VS Code API; application methods remain unchanged. Exigences : DEVAI-135. */ function load(request, parent, main) { return request === 'vscode' ? api : original.call(this, request, parent, main); };
  let View; try { delete require.cache[require.resolve('../dist/view')]; View = require('../dist/view').OdsView; } finally { Module._load = original; }
  const stored = new Map();
  const view = new View({ workspaceState: { get: key => stored.get(key), update: async (key, value) => stored.set(key, value) } }, engine, gateway);
  view.root = { uri: uri('/project') }; view.project = 1042; view.inspection = { commit: 'revision', branch: 'feat/example' };
  return view;
}

/** Drives the actual controller through native patches, build failures and fresh scans without paid model calls. Exigences : DEVAI-090, DEVAI-098, DEVAI-175. */
function remediationFixture(source, options = {}) {
  const events = [], calls = [], order = [], documents = [];
  let content = 'before', turn = 0, branch = 'develop', builds = 0, scans = 0;
  const change = { path: options.coverage ? 'src/test/AppTest.java' : 'src/App.java', baseHash: 'original-hash', content: options.coverage ? 'after tests' : 'after', requirements: ['DEVAI-090'] };
  const api = { Uri: { joinPath: (base, path) => uri(base.fsPath + '/' + path), parse: uri }, workspace: { isTrusted: true, textDocuments: documents }, commands: { executeCommand: async command => { assert.equal(command, 'vscode.diff'); assert.equal(content, 'after'); order.push('diff'); if (options.diffFailure) throw new Error('diff unavailable'); } }, window: {
    showWarningMessage: async () => { throw new Error('Unexpected secondary approval'); }
  } };
  const engine = { call: async (method, args) => {
    calls.push({ method, args });
    if (method === 'project.inspect') return { status: options.gitDirty ? ' M src/App.java' : '', branch };
    if (method === 'process.start') { assert.deepEqual(args.arguments.slice(0, 3), ['git', 'switch', '-c']); branch = args.arguments[3]; order.push('branch'); if (options.stop) view.stopped = true; return { processId: 'branch-process' }; }
    if (method === 'process.cancel') return {};
    if (method === 'process.output') return { running: false, exitCode: options.branchFailure ? 1 : 0 };
    if (method === 'changes.propose') { if (options.dirty) documents.push({ uri: uri('/project/src/App.java'), isDirty: true }); return { changes: args.changes, planHash: 'validated-plan' }; }
    if (method === 'files.read') return { content, sha256: 'original-hash' };
    if (method === 'changes.apply') { assert.equal(args.planHash, 'validated-plan'); assert.equal(args.approved, true); content = 'after'; order.push('apply'); return { applied: 1, backup: 'recoverable-checkpoint' }; }
    throw new Error(method);
  } };
  const gateway = { progress: async () => {}, request: async (route, method, body) => {
    calls.push({ route, body });
    if (route.includes('/fingerprint')) return { fingerprint: options.stale || (options.changedDuringCorrection && turn > 0) ? 'changed' : content === 'before' ? 'original' : 'corrected' };
    if (route === '/api/v1/ai/agents/run') { turn = 0; assert.equal(body.mode, 'AGENT'); assert.equal(body.task, source === 'SONAR' ? 'sonar-remediation' : 'xray-remediation'); assert.match(body.message, /changes_propose/); return { jobId: 'fix-run' }; }
    if (route === '/api/v1/jobs/fix-run') return { status: turn === 1 && !options.noPatch ? 'WAITING_APPROVAL' : 'SUCCEEDED', result: { message: turn === 1 && !options.noPatch ? { tool_calls: [{ id: 'patch', function: { name: 'changes_propose', arguments: JSON.stringify({ changes: [change] }) } }] } : { content: 'Explication du modèle.' } } };
    if (route.endsWith('/turns')) { turn++; return {}; }
    if (route === '/api/v1/workspace/validate') { builds++; order.push('build'); return { jobId: 'validate' }; }
    if (route === '/api/v1/jobs/validate') return { status: 'SUCCEEDED', result: { passed: !options.buildAlwaysFails && (!options.buildFailsOnce || builds > 1), log: 'compiler: incompatible types', fingerprint: options.validationStale ? 'foreign' : 'corrected' } };
    if (route === '/api/v1/security/sbom') { order.push('sbom'); return { jobId: 'sbom' }; }
    if (route === '/api/v1/jobs/sbom') return { status: 'SUCCEEDED', result: { fingerprint: 'corrected' } };
    if (route.endsWith('/scans')) { scans++; order.push('scan'); return { jobId: 'scan' }; }
    if (route === '/api/v1/jobs/scan') return { status: 'SUCCEEDED', result: { fingerprint: options.scanStale ? 'foreign' : 'corrected', issues: options.newIssue && scans === 1 ? [{ key: 'introduced', message: 'New regression' }] : options.issueRemains || options.scanFailsOnce && scans === 1 ? [{ key: 'issue-one', message: 'Finding' }] : [], analysisId: 'fresh-analysis', coverage: options.coverage ? { coverage: scans === 1 ? 50 : 90, lines_to_cover: 10, files: [{ path: 'src/App.java', coverage: scans === 1 ? 50 : 90, lines_to_cover: 10, uncovered_lines: scans === 1 ? 5 : 1 }] } : undefined } };
    return {};
  } };
  const view = createView(api, engine, gateway);
  view.view = { webview: { postMessage: message => events.push(message) } };
  view.reports.set(source, { profile: 'JAVA', fingerprint: 'original', analysisId: 'initial-analysis', issues: [{ key: 'issue-one', path: 'src/App.java', message: 'Finding', severity: 'MAJOR' }], coverage: options.coverage ? { coverage: 20, lines_to_cover: 10, files: [{ path: 'src/App.java', coverage: 20, lines_to_cover: 10, uncovered_lines: 8 }] } : undefined });
  return { view, events, calls, order, content: () => content, outcome: () => events.findLast(event => event.type === 'correctionReport')?.data };
}
test('DEVAI-090: model explanation without an applied patch remains unimplemented', async () => {
  const state = remediationFixture('SONAR');
  state.view.chat = async () => { state.view.remediation.report.items[0].explanation = 'Proposed but not applied'; };
  await state.view.remediate('SONAR');
  assert.equal(state.outcome().items[0].status, 'unimplemented');
  assert.equal(state.outcome().files.length, 0);
});
for (const source of ['SONAR', 'XRAY']) test(`DEVAI-090/098: ${source} branches, applies, validates and rescans before diff`, async () => {
  const state = remediationFixture(source); await state.view.remediate(source);
  assert.deepEqual(state.order, ['branch', 'apply', 'build', ...(source === 'XRAY' ? ['sbom'] : []), 'scan', 'diff']);
  assert.equal(state.content(), 'after'); assert.equal(state.outcome().items[0].status, 'corrected'); assert.equal(state.outcome().validated, true);
  assert.equal(state.outcome().initialAnalysis, 'initial-analysis'); assert.equal(state.outcome().finalAnalysis, 'fresh-analysis');
  assert.ok(state.outcome().checkpoints.includes('recoverable-checkpoint'));
  assert.equal(state.view.remediation, undefined); assert.equal(state.view.remediationBusy, false); assert.equal(state.view.conversation, undefined);
  assert.equal(state.view.grants.size, 0);
});
test('DEVAI-090: failed compilation automatically retries with initial evidence and error log before any diff', async () => {
  const state = remediationFixture('SONAR', { buildFailsOnce: true }); await state.view.remediate('SONAR');
  assert.deepEqual(state.order, ['branch', 'apply', 'build', 'apply', 'build', 'scan', 'diff']);
  const prompts = state.calls.filter(call => call.route === '/api/v1/ai/agents/run');
  assert.equal(prompts.length, 2); assert.match(prompts[1].body.message, /compiler: incompatible types/); assert.match(prompts[1].body.message, /issue-one/);
});
test('DEVAI-090: remaining issue triggers another repair after successful tests', async () => {
  const state = remediationFixture('SONAR', { scanFailsOnce: true }); await state.view.remediate('SONAR');
  assert.equal(state.outcome().rounds, 2); assert.equal(state.outcome().items[0].status, 'corrected'); assert.equal(state.order.filter(value => value === 'diff').length, 1);
});
test('DEVAI-090: coverage automatically retries with regenerated metrics until target is met', async () => {
  const state = remediationFixture('SONAR', { coverage: true }); await state.view.remediate('SONAR', 80);
  assert.equal(state.outcome().rounds, 2); assert.equal(state.outcome().before, 20); assert.equal(state.outcome().after, 90); assert.equal(state.outcome().items[0].status, 'corrected');
  const prompts = state.calls.filter(call => call.route === '/api/v1/ai/agents/run'); assert.match(prompts[1].body.message, /"coverage":50/); assert.match(prompts[1].body.message, /80%/);
});
test('DEVAI-090: exhausted compilation failures never display a validated diff', async () => {
  const state = remediationFixture('SONAR', { buildAlwaysFails: true }); await state.view.remediate('SONAR', undefined, 2);
  assert.equal(state.outcome().rounds, 2); assert.equal(state.outcome().validated, false); assert.equal(state.outcome().items[0].status, 'partial'); assert.equal(state.order.includes('diff'), false);
});
test('DEVAI-090: model prose without a patch never claims a correction', async () => {
  const state = remediationFixture('SONAR', { noPatch: true }); await state.view.remediate('SONAR');
  assert.match(state.outcome().message, /Aucun correctif appliqué/); assert.equal(state.outcome().items[0].status, 'unimplemented'); assert.equal(state.calls.filter(call => call.route?.endsWith('/turns')).length, 2); assert.equal(state.content(), 'before');
});
for (const option of ['stale', 'gitDirty', 'branchFailure', 'stop']) test(`DEVAI-175/077: ${option} prevents model execution`, async () => {
  const state = remediationFixture('SONAR', { [option]: true }); await assert.rejects(() => state.view.remediate('SONAR'));
  assert.equal(state.calls.some(call => call.route?.includes('/ai/')), false); assert.equal(state.view.remediationBusy, false);
});
for (const option of ['dirty', 'changedDuringCorrection']) test(`DEVAI-175/069: ${option} prevents automatic application`, async () => {
  const state = remediationFixture('XRAY', { [option]: true }); await state.view.remediate('XRAY');
  assert.equal(state.content(), 'before'); assert.equal(state.order.includes('apply'), false); assert.equal(state.outcome().items[0].status, 'unimplemented');
});
for (const option of ['validationStale', 'scanStale']) test(`DEVAI-175: ${option} rejects foreign evidence`, async () => {
  const state = remediationFixture('SONAR', { [option]: true }); await state.view.remediate('SONAR');
  assert.equal(state.outcome().validated, false); assert.equal(state.order.includes('diff'), false);
});
test('DEVAI-068: unavailable diff keeps checkpoint and never replays the patch', async () => {
  const state = remediationFixture('SONAR', { diffFailure: true }); await state.view.remediate('SONAR');
  assert.equal(state.calls.filter(call => call.method === 'changes.apply').length, 1); assert.match(state.outcome().message, /Diff indisponible/);
});
test('DEVAI-077: concurrent correction is rejected', async () => {
  const state = remediationFixture('SONAR'); const first = state.view.remediate('SONAR');
  await assert.rejects(() => state.view.remediate('SONAR'), /déjà active/); await first;
});
test('DEVAI-090: invalid attempt limits are rejected before any model call', async () => {
  for (const limit of [0, -1, 1.5, 101, '2', NaN]) {
    const state = remediationFixture('SONAR'); await assert.rejects(() => state.view.remediate('SONAR', undefined, limit), /tentatives/);
    assert.equal(state.calls.some(call => call.route?.includes('/ai/')), false);
  }
});
test('DEVAI-090: newly introduced scan findings require another correction', async () => {
  const state = remediationFixture('SONAR', { newIssue: true }); await state.view.remediate('SONAR');
  assert.equal(state.outcome().rounds, 2); assert.deepEqual(state.outcome().newFindings, []);
  assert.match(state.calls.filter(call => call.route === '/api/v1/ai/agents/run')[1].body.message, /New regression/);
});
test('DEVAI-077: stopping the model stage prevents build, rescan and diff', async () => {
  const state = remediationFixture('SONAR'); state.view.chat = async () => { state.view.stopped = true; };
  await state.view.remediate('SONAR'); assert.deepEqual(state.order, ['branch']); assert.match(state.outcome().message, /arrêtée/); assert.equal(state.outcome().validated, false);
});

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

/** Exercises MR preparation, model completion, preview and publication with synthetic external services only. Exigences : DEVAI-113, DEVAI-135. */
function mrFixture(options = {}) {
  const calls = [], previews = [];
  const evidence = { sourceCommit: 'a'.repeat(40), targetCommit: 'b'.repeat(40), files: [
    { path: 'src/App.java', oldPath: 'src/App.java', status: 'modified', diff: '@@ -1 +1 @@\n-old\n+new' },
    { path: 'new.txt', oldPath: 'old.txt', status: 'renamed', diff: '' }
  ] };
  const response = { summary: 'Changement observé', files: evidence.files.map(file => ({ path: file.path, summary: file.status, recommendation: 'Vérifier les appelants et les liens.', needsClarification: file.status === 'renamed', clarification: file.status === 'renamed' ? 'Les consommateurs utilisent-ils encore old.txt ?' : 'Intention explicite dans le diff.', suggestedTest: 'Exécuter les tests existants.' })) };
  const api = { Uri: { parse: uri }, workspace: { isTrusted: true, openTextDocument: async value => { previews.push(view.provideTextDocumentContent(value)); return {}; } }, window: {
    showInputBox: async spec => spec.title === 'Branche cible' ? 'develop' : spec.title.startsWith('Titre') ? 'Titre MR' : options.cancelComments ? undefined : 'Commentaires existants',
    showTextDocument: async () => {}, showWarningMessage: async () => { if (options.stop) view.stopped = true; if (options.changeHead) head = 'c'.repeat(40); return options.cancel ? undefined : 'Publier'; }
  } };
  let head = evidence.sourceCommit;
  const engine = { call: async method => { assert.equal(method, 'project.inspect'); return { commit: head, branch: 'feat/example', stacks: ['JAVA'] }; } };
  const gateway = { request: async (route, method, body) => {
    calls.push({ route, body });
    if (route.startsWith('/api/v1/git/mr-context?')) return { ...evidence, sourceCommit: options.unpushed ? 'd'.repeat(40) : evidence.sourceCommit };
    if (route === '/api/v1/ai/chat') { assert.equal(body.mode, 'QUESTION'); assert.equal(body.task, 'gitlab-mr-description'); assert.deepEqual(body.context, []); assert.match(body.message, /old.txt/); if (options.failure) throw new Error('Model unavailable'); return { jobId: 'review-job' }; }
    if (route.startsWith('/api/v1/ai/usage')) return {};
    if (route === '/api/v1/git/merge-requests') { assert.equal(previews.length > 0, true); return { iid: 1 }; }
    throw new Error(route);
  } };
  const view = createView(api, engine, gateway);
  view.capabilities = { rule: { aiEnabled: true, allowedActions: ['MR_DESCRIPTION'] } };
  view.approval = async () => !options.refuseContext;
  view.waitJob = async () => ({ status: 'SUCCEEDED', result: { message: { content: options.invalid ? '{}' : JSON.stringify(response) } } });
  return { view, calls, previews, response, evidence };
}

test('DEVAI-113: create MR automatically includes comments and a complete file-by-file pre-review', async () => {
  const state = mrFixture(); await state.view.mr();
  const published = state.calls.find(call => call.route === '/api/v1/git/merge-requests').body;
  assert.match(published.description, /Commentaires existants/); assert.match(published.description, /src\/App.java/);
  assert.match(published.description, /Clarification requise/); assert.match(published.description, /Clarification non requise/);
  assert.match(published.description, /Aucun test exécuté/); assert.equal(published.sourceCommit, state.evidence.sourceCommit);
  assert.equal(published.description, state.previews[0]); assert.equal(state.view.mrBusy, false);
});

test('DEVAI-113: preparing then creating reuses the same immutable review without another model call', async () => {
  const state = mrFixture(); await state.view.mrDescription();
  assert.equal(state.calls.some(call => call.route === '/api/v1/git/merge-requests'), false);
  await state.view.mr(); assert.equal(state.calls.filter(call => call.route === '/api/v1/ai/chat').length, 1);
  state.evidence.targetCommit = 'e'.repeat(40); await state.view.mr();
  assert.equal(state.calls.filter(call => call.route === '/api/v1/ai/chat').length, 2);
});

for (const scenario of ['cancel', 'cancelComments', 'refuseContext', 'stop', 'changeHead', 'failure', 'invalid', 'unpushed']) {
  test(`DEVAI-113: ${scenario} never publishes a partial or stale pre-review`, async () => {
    const state = mrFixture({ [scenario]: true });
    if (['cancel', 'cancelComments'].includes(scenario)) await state.view.mr(); else await assert.rejects(() => state.view.mr());
    assert.equal(state.calls.some(call => call.route === '/api/v1/git/merge-requests'), false); assert.equal(state.view.mrBusy, false);
  });
}

test('DEVAI-113: incomplete, duplicate, invented paths and missing clarification are rejected', () => {
  const { renderMrReview } = require('../dist/mrReview');
  for (const mutate of [review => review.files.pop(), review => review.files[1].path = review.files[0].path, review => review.files[0].path = 'invented', review => delete review.files[0].clarification, review => delete review.files[0].needsClarification]) {
    const state = mrFixture(); mutate(state.response); assert.throws(() => renderMrReview(JSON.stringify(state.response), state.evidence));
  }
});

test('DEVAI-113: policy denial prevents even fetching the review evidence', async () => {
  const state = mrFixture(); state.view.capabilities.rule.allowedActions = [];
  await assert.rejects(() => state.view.mr(), /MR_DESCRIPTION/); assert.equal(state.calls.length, 0);
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

/** Exercises real native document orchestration with only Git, engine filesystem and LLM boundary doubles. Exigences : DEVAI-180, DEVAI-181, DEVAI-177. */
function documentFixture(filename, previous, options = {}) {
  const crypto = require('node:crypto'), files = new Map(), events = [], calls = [];
  if (previous !== undefined) files.set(filename, previous);
  const hash = content => content === undefined ? 'MISSING' : crypto.createHash('sha256').update(content).digest('hex');
  const baseHash = hash(previous), content = (previous || '# Document\n') + '\n## Non publié\n- Changement observé.\n';
  const api = { Uri: { joinPath: (base, file) => uri(base.fsPath + '/' + file), parse: uri }, workspace: { isTrusted: true, textDocuments: [] }, commands: { executeCommand: async command => { assert.equal(command, 'vscode.diff'); } }, window: { showQuickPick: async values => values, showWarningMessage: async text => options.refuse && text.startsWith('Appliquer') ? undefined : 'Autoriser cette action' } };
  const engine = { call: async (method, args) => {
    calls.push({ method, args });
    if (method === 'project.inspect') return { commit: 'a'.repeat(40), branch: 'feat/example', stacks: ['JAVA'], status: ' M src/App.java' };
    if (method === 'workspace.list') return [...files.keys()];
    if (method === 'files.read') { assert.ok(files.has(args.path)); return { content: files.get(args.path), sha256: hash(files.get(args.path)) }; }
    if (method === 'process.start') { assert.deepEqual(args.arguments.slice(0, 3), ['git', 'log', '-30']); return { processId: 'history' }; }
    if (method === 'process.output') return { running: false, exitCode: 0, output: 'abcdef1 feat: observed change', truncated: false };
    if (method === 'changes.propose') { assert.equal(args.changes[0].baseHash, hash(files.get(filename))); return { changes: args.changes, planHash: 'plan' }; }
    if (method === 'changes.apply') { files.set(filename, args.changes[0].content); return { applied: 1, backup: 'checkpoint' }; }
    throw new Error(method);
  } };
  let turn = 0;
  const gateway = { progress: async () => {}, request: async (route, method, body) => {
    calls.push({ route, body });
    if (route === '/api/v1/ai/chat') { if (options.failure) throw new Error('Synthetic LLM failure'); assert.equal(body.mode, 'MODIFICATION'); assert.equal(body.task, filename === 'CHANGELOG.md' ? 'git-changelog' : 'git-release-notes'); assert.ok(body.message.includes(baseHash)); return { jobId: 'doc-run' }; }
    if (route === '/api/v1/jobs/doc-run') return { status: turn === 0 ? 'WAITING_APPROVAL' : 'SUCCEEDED', result: { message: turn === 0 ? { tool_calls: [{ id: 'patch', function: { name: 'changes_propose', arguments: JSON.stringify({ changes: [{ path: options.changePath || filename, baseHash, content, requirements: ['DEVAI-180', 'DEVAI-181'] }] }) } }] } : { content: 'Résultat du traitement.' } } };
    if (route.endsWith('/turns')) { turn++; return {}; }
    return {};
  } };
  const view = createView(api, engine, gateway); view.capabilities = { rule: { aiEnabled: true, contextMode: 'LOCAL_GOVERNED', allowedActions: ['CHANGELOG', 'RELEASE_NOTES'] } };
  view.view = { webview: { postMessage: data => events.push(data) } };
  return { view, files, events, calls, content };
}

for (const filename of ['CHANGELOG.md', 'RELEASE_NOTES.md']) for (const exists of [false, true]) {
  test(`DEVAI-180/181: ${exists ? 'updates' : 'creates'} ${filename} from a mocked LLM through the real reviewed patch flow`, async () => {
    const previous = exists ? '# Historique\n\n## 1.0.0\n- Version précédente à conserver.\n' : undefined;
    const state = documentFixture(filename, previous);
    await state.view.gitDocument(filename);
    assert.equal(state.files.get(filename), state.content);
    if (exists) assert.ok(state.files.get(filename).startsWith(previous));
    assert.equal(state.events.find(item => item.type === 'git').data.updated, true);
    assert.equal(state.view.documentationTarget, undefined);
  });
}

test('DEVAI-180/181: denied rights never reach Git or the model', async () => {
  const state = documentFixture('CHANGELOG.md'); state.view.capabilities.rule.allowedActions = ['RELEASE_NOTES'];
  await assert.rejects(state.view.gitDocument('CHANGELOG.md'), /autoriser CHANGELOG/);
  assert.equal(state.calls.length, 0); assert.equal(state.files.size, 0);
});
test('DEVAI-180/181: a model proposal outside the selected document cannot alter code', async () => {
  const state = documentFixture('CHANGELOG.md', 'Previous', { changePath: 'src/App.java' });
  await state.view.gitDocument('CHANGELOG.md');
  assert.equal(state.files.get('CHANGELOG.md'), 'Previous'); assert.equal(state.files.has('src/App.java'), false);
  assert.equal(state.events.find(item => item.type === 'git').data.updated, false);
  assert.ok(state.calls.some(item => item.route?.endsWith('/tool-results') && item.body.result.error));
  state.view.conversation = { task: 'git-changelog' };
  for (const content of [null, '', '   ']) await assert.rejects(state.view.tool('changes_propose', { changes: [{ path: 'CHANGELOG.md', baseHash: 'unused', content }] }), /uniquement créer ou mettre à jour/);
  assert.equal(state.files.get('CHANGELOG.md'), 'Previous');
});
test('DEVAI-180/181: refusing the diff preserves existing release notes', async () => {
  const state = documentFixture('RELEASE_NOTES.md', 'Previous release', { refuse: true });
  await state.view.gitDocument('RELEASE_NOTES.md');
  assert.equal(state.files.get('RELEASE_NOTES.md'), 'Previous release');
  assert.equal(state.events.find(item => item.type === 'git').data.updated, false);
});
test('DEVAI-180/181: model failure leaves files unchanged and releases the busy state', async () => {
  const state = documentFixture('RELEASE_NOTES.md', 'Previous release', { failure: true });
  await assert.rejects(state.view.gitDocument('RELEASE_NOTES.md'), /Synthetic LLM failure/);
  assert.equal(state.files.get('RELEASE_NOTES.md'), 'Previous release');
  assert.equal(state.view.documentationTarget, undefined); assert.deepEqual(state.events.at(-1), { type: 'busy', value: false });
});
