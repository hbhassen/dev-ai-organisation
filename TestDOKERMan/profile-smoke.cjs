/* Runs in the real VS Code Test extension host. Only presentation capture/storage and
 * provider HTTP are synthetic; Java IPC, gateway, Vibe SDK and local GitLab are real. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const vscode = require('vscode');
const local = path.join(__dirname, '.local');

async function until(condition, label, timeout = 150000) {
  const deadline = Date.now() + timeout;
  while (!condition()) {
    if (Date.now() > deadline) throw new Error('Timeout: ' + label);
    await new Promise(resolve => setTimeout(resolve, 250));
  }
}

async function run() {
  const evidence = {profile: 'Test', realMistralCalls: 0, checks: [], status: 'RUNNING'};
  let engine, gateway;
  try {
    const extension = vscode.extensions.getExtension('ods-local.ods-docker-manual');
    assert.ok(extension); assert.equal(extension.packageJSON.version, '0.3.2');
    await extension.activate();
    assert.ok((await vscode.commands.getCommands(true)).includes('ods.open'));
    evidence.checks.push('VSIX 0.3.2 activation in real VS Code extension host');
    const extensionPath = fs.readFileSync(path.join(local, 'automation-stage.txt'), 'utf8').trim();
    // Explicitly use the isolated synthetic fixture, never the installed provider credentials.
    const deployment = JSON.parse(fs.readFileSync(path.join(extensionPath, 'runtime/deployment.json')));
    assert.equal(deployment.environment.ODS_VIBE_URL, 'https://mistral.invalid/v1');
    assert.equal(deployment.environment.ODS_MISTRAL_API_KEY, 'synthetic-profile-test');
    assert.equal(deployment.environment.ODS_MISTRAL_MODEL, 'mistral-medium-3-5');
    const { EngineClient } = require(path.join(extension.extensionPath, 'dist/engine.js'));
    const { GatewayClient } = require(path.join(extension.extensionPath, 'dist/gateway.js'));
    const { OdsView } = require(path.join(extension.extensionPath, 'dist/view.js'));
    const storage = new Map(), secrets = new Map();
    const context = {extensionPath, extension, extensionUri: vscode.Uri.file(extensionPath),
      globalStorageUri: vscode.Uri.file(path.join(local, 'host-smoke-storage', randomUUID())),
      workspaceState: {get: key => storage.get(key), update: async (key, value) => storage.set(key, value)},
      secrets: {get: async key => secrets.get(key), store: async (key, value) => secrets.set(key, value)}};
    engine = new EngineClient(extension.extensionPath); gateway = new GatewayClient(context);
    const view = new OdsView(context, engine, gateway), messages = [];
    let receive;
    view.resolveWebviewView({webview: {cspSource: 'vscode-webview:', asWebviewUri: uri => uri,
      onDidReceiveMessage: callback => { receive = callback; return {dispose() {}}; },
      postMessage: async message => { messages.push(message); return true; }}});
    await until(() => messages.some(m => m.type === 'capabilities'), 'initial policy');
    const capabilities = messages.find(m => m.type === 'capabilities');
    assert.equal(capabilities.rule?.aiEnabled, true, capabilities.reason);
    await until(() => messages.some(m => m.type === 'tasks'), 'prompts');
    evidence.checks.push('native OdsView refresh -> real GitLab access and signed policy -> presentation');
    const fingerprint = await gateway.request('/api/v1/workspace/fingerprint?projectId=' + capabilities.projectId);
    assert.match(fingerprint.fingerprint, /^[a-f0-9]{64}$/);
    evidence.checks.push('HTTP fingerprint has the exact frontend field');
    // Only synthetic provider transfers are preapproved; this test performs no writes or commands.
    view.grants.add('context');
    for (const [mode, expected] of [['success', null], ['tool', null], ['401', 'MODEL_AUTHENTICATION_FAILED'], ['429', 'MODEL_RATE_LIMITED'], ['404', 'MODEL_UNAVAILABLE']]) {
      view.conversation = undefined;
      fs.writeFileSync(path.join(extensionPath, 'runtime/fixture-mode.txt'), mode);
      const start = messages.length;
      receive({type: 'chat', mode: 'QUESTION', task: 'code-generation', message: 'analyser le code', includeActive: false});
      await until(() => messages.slice(start).some(m => m.type === 'busy' && m.value === false), 'chat ' + mode);
      const emitted = messages.slice(start), error = emitted.find(m => m.type === 'error');
      assert.ok(emitted.some(m => m.type === 'accepted'));
      if (expected) {
        await until(() => messages.slice(start).some(m => m.type === 'error'), 'error presentation');
        assert.ok(messages.slice(start).find(m => m.type === 'error').message.startsWith(expected));
      } else {
        assert.equal(error, undefined);
        assert.ok(emitted.some(m => m.type === 'chat' && m.role === 'assistant' && m.text.includes('communication front gateway Vibe validee')));
        assert.ok(emitted.some(m => m.type === 'usage' && m.total_tokens === 30));
        if (mode === 'tool') assert.ok(emitted.some(m => m.type === 'tool' && m.name === 'files_read'));
      }
      assert.ok(!JSON.stringify(messages.slice(start)).includes('synthetic-private-provider-detail'));
      evidence.checks.push('IA native message -> gateway -> real Vibe CLI -> presentation: ' + mode);
    }
    const calls = fs.readFileSync(path.join(extensionPath, 'runtime/fixture-calls.jsonl'), 'utf8').trim().split('\n').map(JSON.parse);
    assert.equal(calls.length, 6); assert.ok(calls.every(call => call.synthetic));
    evidence.syntheticHttpCalls = calls.length;
    if (process.env.ODS_MANUAL_REAL_MISTRAL === '1') {
      const real = new GatewayClient({...context, extensionPath: extension.extensionPath,
        globalStorageUri: vscode.Uri.file(path.join(local, 'real-smoke-storage', randomUUID()))});
      try {
        await real.connect(vscode.workspace.workspaceFolders[0]);
        const realEvidence = {model: 'mistral-medium-3-5', submittedJobs: 1, status: 'SUBMITTED'};
        fs.writeFileSync(path.join(local, 'real-mistral-result.json'), JSON.stringify(realEvidence, null, 2));
        const job = await real.request('/api/v1/ai/chat', 'POST', {projectId: capabilities.projectId, mode: 'QUESTION',
          task: 'code-generation', stacks: ['JAVA'], context: [],
          message: 'Verification de connexion uniquement. Reponds exactement ODS_OK. Ne lis aucun fichier et ne demande aucun outil.'});
        // Exactly one submission. A tool request is recorded, never executed or continued.
        let result;
        const deadline = Date.now() + 180000;
        do {
          result = await real.request('/api/v1/jobs/' + job.jobId);
          if (!['QUEUED', 'RUNNING'].includes(result.status)) break;
          await new Promise(resolve => setTimeout(resolve, 1000));
        } while (Date.now() < deadline);
        Object.assign(realEvidence, {status: result.status, usage: result.result?.usage,
          answerMatches: result.result?.message?.content?.trim() === 'ODS_OK', failureCode: result.result?.code});
        fs.writeFileSync(path.join(local, 'real-mistral-result.json'), JSON.stringify(realEvidence, null, 2));
        evidence.realMistralCalls = 1; evidence.realMistral = realEvidence;
        assert.equal(result.status, 'SUCCEEDED', result.result?.code);
        assert.equal(realEvidence.answerMatches, true);
        evidence.checks.push('one real Mistral Medium 3.5 completion through installed gateway/Vibe: ODS_OK');
      } finally { real.dispose(); }
    }
    evidence.status = 'SUCCESS';
  } catch (error) { evidence.status = 'FAILED'; evidence.error = gateway ? gateway.safe(error.message) : error.message; throw error; }
  finally {
    gateway?.dispose(); engine?.dispose();
    fs.writeFileSync(path.join(local, 'profile-smoke-result.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
  }
}
exports.run = run;
