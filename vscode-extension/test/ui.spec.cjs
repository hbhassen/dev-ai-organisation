const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const { renderView } = require('../dist/presentation');

/** Loads the exact product template and resources while replacing only the VS Code message bridge. Exigences : DEVAI-018, DEVAI-019, DEVAI-135. */
async function open(page, width = 900) {
  await page.setViewportSize({ width, height: 1000 });
  await page.addInitScript(() => { window.sent = []; window.acquireVsCodeApi = () => ({ postMessage: message => window.sent.push(message) }); });
  await page.route('http://ods.test/**', route => {
    const resource = new URL(route.request().url()).pathname;
    if (resource === '/') return route.fulfill({ contentType: 'text/html', body: renderView('http://ods.test/view.js', 'http://ods.test/style.css', 'test-nonce', 'http://ods.test', 'ORGANISATION DEVELOPMENT STANDARD', require('../package.json').version) });
    if (!['/view.js', '/style.css'].includes(resource)) return route.abort();
    return route.fulfill({ contentType: resource.endsWith('css') ? 'text/css' : 'text/javascript', body: fs.readFileSync(path.join(__dirname, '../media', resource.slice(1)), 'utf8') });
  });
  await page.goto('http://ods.test/');
  await message(page, { type: 'context', name: 'payment-service', branch: 'main', stacks: ['Spring Boot', 'pom.xml'] });
  await message(page, { type: 'capabilities', projectId: 1042, policyVersion: 3, rule: { aiEnabled: true, allowedActions: ['CHANGELOG', 'RELEASE_NOTES'], modelAlias: 'approved', monthlyTokenQuota: 100000 }, usage: { consumed: 42500, reserved: 0 } });
}

/** Sends host presentation data through the same browser event used by VS Code. Exigences : DEVAI-018, DEVAI-135. */
async function message(page, data) { await page.evaluate(value => window.postMessage(value, '*'), data); }

for (const source of ['SONAR', 'XRAY']) test(`DEVAI-090/098: ${source} correction switches to Agent and disables concurrent scans`, async ({ page }) => {
  await open(page);
  const task = source === 'SONAR' ? 'sonar-remediation' : 'xray-remediation';
  await message(page, { type: 'tasks', tasks: [{ id: task, label: 'Correction' }] });
  await page.getByRole('tab', { name: source === 'SONAR' ? 'Quality' : 'Security', exact: true }).click();
  await page.locator(`[data-remediate="${source}"]`).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'remediate', source });
  await message(page, { type: 'busy', value: true });
  await message(page, { type: 'remediation', mode: 'AGENT', task });
  await expect(page.locator('#ai')).toBeVisible(); await expect(page.locator('#mode')).toHaveValue('AGENT'); await expect(page.locator('#task')).toHaveValue(task);
  for (const button of await page.locator('[data-scan], [data-remediate]').all()) await expect(button).toBeDisabled();
  await message(page, { type: 'busy', value: false });
  await expect(page.locator(`[data-remediate="${source}"]`)).toBeEnabled();
});

for (const width of [900, 330]) {
  test(`DEVAI-013/019: five equal icon tabs and keyboard navigation at ${width}px`, async ({ page }) => {
    await open(page, width);
    await expect(page.getByRole('tab')).toHaveText(['Starter', 'IA', 'Quality', 'Security', 'GitFlow']);
    await expect(page.locator('[role=tab] svg')).toHaveCount(5);
    const boxes = await page.getByRole('tab').evaluateAll(tabs => tabs.map(tab => ({ width: tab.getBoundingClientRect().width, y: tab.getBoundingClientRect().y })));
    expect(new Set(boxes.map(box => box.y)).size).toBe(1); expect(Math.max(...boxes.map(box => box.width)) - Math.min(...boxes.map(box => box.width))).toBeLessThan(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.getByRole('tab', { name: 'IA', exact: true }).focus(); await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('tab', { name: 'Quality', exact: true })).toBeFocused(); await expect(page.locator('#quality')).toBeVisible();
    await page.keyboard.press('End'); await expect(page.locator('#git')).toBeVisible(); await page.keyboard.press('Home'); await expect(page.locator('#starter')).toBeVisible();
    await page.getByRole('tab', { name: 'IA', exact: true }).click();
    await expect(page.locator('#quota')).toHaveAttribute('value', '42500');
    await page.screenshot({ path: path.join(__dirname, `../../.artifacts/interface-${width}.png`), fullPage: true });
  });
}

test('DEVAI-063/077/151: composer sends once, preserves rejected draft and acknowledges accepted text', async ({ page }) => {
  await open(page);
  const composer = page.getByRole('textbox', { name: 'Votre demande' });
  await composer.fill('Examiner le projet'); await composer.press('Shift+Enter'); await composer.press('X');
  await expect(composer).toHaveValue('Examiner le projet\nX');
  await composer.press('Enter'); await composer.press('Enter');
  expect(await page.evaluate(() => window.sent.filter(item => item.type === 'chat').length)).toBe(1);
  await expect(page.getByRole('button', { name: 'Arrêter', exact: true })).toBeVisible();
  await message(page, { type: 'busy', value: false }); await expect(composer).toHaveValue('Examiner le projet\nX');
  await page.getByRole('button', { name: 'Envoyer la demande' }).click();
  await message(page, { type: 'accepted', text: 'Examiner le projet\nX' }); await expect(composer).toHaveValue('');
  await message(page, { type: 'busy', value: false }); await expect(page.getByRole('button', { name: 'Envoyer la demande' })).toBeDisabled();
});

test('DEVAI-018/042/088: hostile text stays inert, attachments and report pagination dispatch typed actions', async ({ page }) => {
  await open(page);
  const hostile = '<img src=x onerror="window.injected=true">';
  await message(page, { type: 'chat', role: 'assistant', text: hostile }); await expect(page.locator('#conversation img')).toHaveCount(0);
  await message(page, { type: 'attachments', paths: ['pom.xml'] }); await page.getByRole('button', { name: 'Retirer pom.xml' }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'detach', path: 'pom.xml' });
  await message(page, { type: 'report', source: 'SONAR', data: { source: 'SONAR', issues: [{ message: hostile, severity: 'MAJOR', path: 'Main.java' }], paging: { pageIndex: 1, pageSize: 50, total: 51 } } });
  await page.getByRole('tab', { name: 'Quality', exact: true }).click(); await page.getByRole('button', { name: 'Suivant', exact: true }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'report', source: 'SONAR', page: 2 });
  expect(await page.evaluate(() => window.injected)).toBeUndefined();
});

test('DEVAI-013/151: capability denial disables sending while leaving non-AI tabs available', async ({ page }) => {
  await open(page); await message(page, { type: 'capabilities', aiEnabled: false, reason: 'ACTION_DENIED' });
  await page.locator('#message').fill('Question'); await expect(page.locator('#send')).toBeDisabled();
  await page.getByRole('tab', { name: 'Security', exact: true }).click(); await expect(page.getByRole('button', { name: 'Générer rapport Xray' })).toBeEnabled();
  await page.getByRole('tab', { name: 'GitFlow', exact: true }).click(); await expect(page.getByRole('button', { name: 'Cloner', exact: true })).toBeEnabled();
});

test('DEVAI-013/019/045: all tabs dispatch their native action and remain usable in a dark theme', async ({ page }) => {
  await open(page, 330);
  await page.evaluate(() => { const theme = { '--vscode-foreground': '#e4e4e4', '--vscode-descriptionForeground': '#aaa', '--vscode-sideBar-background': '#181818', '--vscode-editor-background': '#202020', '--vscode-panel-border': '#414141', '--vscode-button-secondaryBackground': '#303030', '--vscode-button-secondaryForeground': '#eee', '--vscode-input-background': '#202020', '--vscode-input-foreground': '#eee', '--vscode-textLink-foreground': '#61c5b1' }; for (const [key, value] of Object.entries(theme)) document.documentElement.style.setProperty(key, value); });
  await page.getByRole('tab', { name: 'Starter', exact: true }).click(); await page.locator('#name').fill('payment-service'); await page.locator('#create').click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'starter', stack: 'JAVA', name: 'payment-service' });
  await page.getByRole('tab', { name: 'Quality', exact: true }).click(); await page.getByRole('button', { name: 'Analyser', exact: true }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'scan', source: 'SONAR' });
  await page.getByRole('tab', { name: 'Security', exact: true }).click(); await page.getByRole('button', { name: 'Générer SBOM', exact: true }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'sbom' });
  await page.getByRole('button', { name: 'Générer rapport Xray', exact: true }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'scan', source: 'XRAY' });
  await page.getByRole('tab', { name: 'GitFlow', exact: true }).click(); await page.getByRole('button', { name: 'Créer une MR', exact: true }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'mr' });
  await page.getByRole('tab', { name: 'IA', exact: true }).click(); await page.screenshot({ path: path.join(__dirname, '../../.artifacts/interface-dark.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('DEVAI-179: Enter reaches the real native controller and HTTP client, then displays the mocked LLM answer', async ({ page }) => {
  const http = require('node:http'), Module = require('node:module');
  const requests = [], stored = new Map(); let completion;
  const server = http.createServer(async (request, response) => {
    assertLocalToken(request); response.setHeader('Content-Type', 'application/json');
    if (request.url === '/api/v1/ai/chat') {
      let body = ''; for await (const chunk of request) body += chunk;
      const input = JSON.parse(body); requests.push(input);
      completion = { message: { role: 'assistant', content: 'Réponse LLM simulée : ' + input.message }, usage: { prompt_tokens: 8, completion_tokens: 5, total_tokens: 13 } };
      response.end(JSON.stringify({ jobId: 'mock-run' }));
    } else if (request.url === '/api/v1/jobs/mock-run') response.end(JSON.stringify({ status: 'SUCCEEDED', result: completion }));
    else if (request.url.startsWith('/api/v1/ai/usage')) response.end(JSON.stringify({ consumed: 13, reserved: 0 }));
    else { response.statusCode = 404; response.end('{}'); }
  });
  /** Checks that the actual HTTP adapter retains its private native launch credential. Exigences : DEVAI-168, DEVAI-179. */
  function assertLocalToken(request) { expect(request.headers['x-ods-local-token']).toBe('synthetic-launch'); }
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const root = { uri: { toString: () => 'file:///synthetic-project', fsPath: '/synthetic-project' } };
  const api = { workspace: { isTrusted: true, textDocuments: [] }, window: { showWarningMessage: async () => 'Autoriser cette action' } };
  const original = Module._load; let OdsView, GatewayClient;
  Module._load = function load(request, parent, main) { return request === 'vscode' ? api : original.call(this, request, parent, main); };
  try { delete require.cache[require.resolve('../dist/view')]; delete require.cache[require.resolve('../dist/gateway')]; OdsView = require('../dist/view').OdsView; GatewayClient = require('../dist/gateway').GatewayClient; }
  finally { Module._load = original; }
  const context = { workspaceState: { get: key => stored.get(key), update: async (key, value) => stored.set(key, value) } };
  const gateway = new GatewayClient(context); gateway.root = root; gateway.runtime = { base: () => new URL(`http://127.0.0.1:${server.address().port}`), token: () => 'synthetic-launch' };
  const view = new OdsView(context, {}, gateway); view.root = root; view.project = 1042; view.inspection = { stacks: ['JAVA'] };
  try {
    await open(page);
    view.view = { webview: { postMessage: data => message(page, data) } };
    await page.exposeFunction('nativeDispatch', data => view.handle(data));
    await page.evaluate(() => { vscode.postMessage = data => { window.sent.push(data); void window.nativeDispatch(data); }; });
    const composer = page.locator('#message'); await composer.fill('Vérifier le code');
    await composer.dispatchEvent('keydown', { key: 'Enter', isComposing: true }); expect(requests).toHaveLength(0);
    await composer.press('Enter');
    await expect(page.locator('#conversation')).toContainText('Réponse LLM simulée : Vérifier le code');
    await expect(composer).toHaveValue(''); expect(requests).toHaveLength(1); expect(requests[0].message).toBe('Vérifier le code');
    await expect(page.getByRole('button', { name: /SSO/ })).toHaveCount(0);
  } finally { await new Promise(resolve => server.close(resolve)); }
});

for (const width of [900, 330]) {
  test(`DEVAI-180/181: GitFlow document buttons dispatch distinct actions and respect rights at ${width}px`, async ({ page }) => {
    await open(page, width);
    await page.getByRole('tab', { name: 'GitFlow', exact: true }).click();
    await page.getByRole('button', { name: 'Changelog', exact: true }).click();
    expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'changelog' });
    await page.getByRole('button', { name: 'Release notes', exact: true }).click();
    expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'releaseNotes' });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: path.join(__dirname, `../../.artifacts/gitflow-${width}.png`), fullPage: true });
    await page.locator('#git').screenshot({ path: path.join(__dirname, `../../.artifacts/gitflow-panel-${width}.png`) });
    await message(page, { type: 'busy', value: true });
    await expect(page.getByRole('button', { name: 'Changelog', exact: true })).toBeDisabled();
    await message(page, { type: 'busy', value: false });
    await message(page, { type: 'capabilities', rule: { aiEnabled: true, allowedActions: ['CHANGELOG'] } });
    await expect(page.getByRole('button', { name: 'Changelog', exact: true })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'Release notes', exact: true })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Cloner', exact: true })).toBeEnabled();
  });
}
