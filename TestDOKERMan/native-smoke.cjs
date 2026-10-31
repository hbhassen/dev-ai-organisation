/* Exercise the exact compiled native frontend clients against the real local gateway.
 * No AI generation endpoint is called. VS Code storage/configuration is the only stub. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const { pathToFileURL } = require('node:url');
const { randomUUID } = require('node:crypto');
const root = __dirname;
const local = path.join(root, '.local');
const state = JSON.parse(fs.readFileSync(path.join(local, 'state.json')));
const extensionPath = path.resolve(process.argv[2]);
const java = path.join(local, 'jdk/bin/java.exe');
const original = Module._load;
Module._load = function (name, parent, isMain) {
  if (name === 'vscode') return {
    workspace: { isTrusted: true, getConfiguration: () => ({get: (key, fallback) => key === 'javaPath' ? java : fallback}) },
    authentication: {getSession: async () => undefined}
  };
  return original.call(this, name, parent, isMain);
};
const { GatewayClient } = require(path.join(extensionPath, 'dist/gateway.js'));
const { EngineClient } = require(path.join(extensionPath, 'dist/engine.js'));

async function waitJob(client, job) {
  const deadline = Date.now() + 900000;
  while (Date.now() < deadline) {
    const current = await client.request('/api/v1/jobs/' + job.jobId);
    if (current.status === 'SUCCEEDED') return current;
    if (['FAILED', 'CANCELLED'].includes(current.status)) throw new Error(JSON.stringify(current.result));
    await new Promise(resolve => setTimeout(resolve, 1500));
  }
  throw new Error('Local scan timed out');
}

async function main() {
  const evidence = {model: state.model, mistralGenerationCalls: 0, checks: [], failures: []};
  async function probe(label, operation) {
    try { const result = await operation(); evidence.checks.push(label); console.log('PASS: ' + label); return result; }
    catch (error) { evidence.failures.push({check: label, error: error.message}); console.error('FAIL: ' + label + ': ' + error.message); }
  }
  for (const name of ['demo-allowed', 'demo-denied']) {
    const folderPath = path.join(local, 'workspaces', name);
    const folder = {name, index: 0, uri: {fsPath: folderPath, toString: () => pathToFileURL(folderPath).href}};
    const secretValues = new Map();
    const context = {extensionPath, globalStorageUri: {fsPath: path.join(local, 'smoke-storage', name, randomUUID())},
      secrets: {get: async key => secretValues.get(key), store: async (key, value) => secretValues.set(key, value)}};
    const client = new GatewayClient(context);
    const engine = new EngineClient(extensionPath);
    try {
      await engine.start();
      await engine.open(folder);
      const inspection = await engine.call('project.inspect');
      assert.ok(inspection.stacks.includes('JAVA'));
      evidence.checks.push(name + ': real Java engine IPC');
      await client.connect(folder);
      const project = await client.request('/api/v1/projects/resolve?path=' + encodeURIComponent('ods-manual/' + name));
      assert.equal(project.projectId, state[name]);
      const caps = await client.request('/api/v1/capabilities?projectId=' + project.projectId);
      assert.equal(caps.rule.aiEnabled, name === 'demo-allowed');
      evidence.checks.push(name + ': Git developer access, HTTPS GitLab, signed policy, AI ' + (caps.rule.aiEnabled ? 'allowed' : 'denied'));
      console.log(name + ': Git access and signed policy verified.');
      const anonymous = await fetch(new URL('/api/v1/capabilities?projectId=' + project.projectId, client.base()));
      assert.ok([401, 403].includes(anonymous.status));
      evidence.checks.push(name + ': anonymous gateway access rejected');
      if (name === 'demo-allowed') {
        await probe('workspace fingerprint HTTP response contract', async () => {
          const fingerprint = await client.request('/api/v1/workspace/fingerprint?projectId=' + project.projectId);
          assert.equal(typeof fingerprint.fingerprint, 'string', 'Fingerprint API response: ' + JSON.stringify(fingerprint));
          assert.equal(fingerprint.fingerprint.length, 64);
        });
        console.log('Starting real Maven CycloneDX generation.');
        const bom = await probe('saved workspace -> Maven CycloneDX SBOM', async () => {
          const result = await waitJob(client, await client.request('/api/v1/security/sbom', 'POST', {projectId: project.projectId, profile: 'JAVA'}));
          assert.equal(result.result.sbom.bomFormat, 'CycloneDX');
          return result;
        });
        if (bom) {
          console.log('Starting simulated Xray Graph Scan.');
          await probe('frontend GatewayClient -> gateway -> HTTPS mockXray -> normalized report', async () => {
            const scan = await waitJob(client, await client.request('/api/v1/security/scans', 'POST', {projectId: project.projectId, profile: 'JAVA', sbomJobId: bom.jobId}));
            assert.ok(scan.result.issues.some(issue => issue.key === 'XRAY-MOCK-0001'));
          });
        }
        console.log('Starting real SonarScanner and SonarQube analysis.');
        await probe('frontend GatewayClient -> gateway -> Maven SonarScanner -> HTTPS SonarQube -> report', async () => {
          const sonar = await waitJob(client, await client.request('/api/v1/quality/scans', 'POST', {projectId: project.projectId, profile: 'JAVA'}));
          assert.equal(sonar.result.source, 'SONAR');
        });
      }
      console.log(name + ': integration checks completed.');
    } catch (error) {
      evidence.failures.push({check: name + ': setup and policy checks', error: client.safe(error.message)});
    } finally { client.dispose(); engine.dispose(); }
  }
  evidence.status = evidence.failures.length ? 'FAILED' : 'SUCCESS';
  fs.writeFileSync(path.join(local, 'native-smoke-result.json'), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify(evidence, null, 2));
  if (evidence.failures.length) process.exitCode = 1;
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
