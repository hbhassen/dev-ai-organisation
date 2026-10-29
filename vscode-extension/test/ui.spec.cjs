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
    if (resource === '/') return route.fulfill({ contentType: 'text/html', body: renderView('http://ods.test/view.js', 'http://ods.test/style.css', 'test-nonce', 'http://ods.test', 'ORGANISATION DEVELOPMENT STANDARD', '0.2.0') });
    if (!['/view.js', '/style.css'].includes(resource)) return route.abort();
    return route.fulfill({ contentType: resource.endsWith('css') ? 'text/css' : 'text/javascript', body: fs.readFileSync(path.join(__dirname, '../media', resource.slice(1)), 'utf8') });
  });
  await page.goto('http://ods.test/');
  await message(page, { type: 'context', name: 'payment-service', branch: 'main', stacks: ['Spring Boot', 'pom.xml'] });
  await message(page, { type: 'capabilities', projectId: 1042, policyVersion: 3, rule: { aiEnabled: true, modelAlias: 'approved', monthlyTokenQuota: 100000 }, usage: { consumed: 42500, reserved: 0 } });
}

/** Sends host presentation data through the same browser event used by VS Code. Exigences : DEVAI-018, DEVAI-135. */
async function message(page, data) { await page.evaluate(value => window.postMessage(value, '*'), data); }

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
  await page.getByRole('tab', { name: 'Security', exact: true }).click(); await expect(page.getByRole('button', { name: 'Auditer le commit' })).toBeEnabled();
  await page.getByRole('tab', { name: 'GitFlow', exact: true }).click(); await expect(page.getByRole('button', { name: 'Cloner', exact: true })).toBeEnabled();
});

test('DEVAI-013/019/045: all tabs dispatch their native action and remain usable in a dark theme', async ({ page }) => {
  await open(page, 330);
  await page.evaluate(() => { const theme = { '--vscode-foreground': '#e4e4e4', '--vscode-descriptionForeground': '#aaa', '--vscode-sideBar-background': '#181818', '--vscode-editor-background': '#202020', '--vscode-panel-border': '#414141', '--vscode-button-secondaryBackground': '#303030', '--vscode-button-secondaryForeground': '#eee', '--vscode-input-background': '#202020', '--vscode-input-foreground': '#eee', '--vscode-textLink-foreground': '#61c5b1' }; for (const [key, value] of Object.entries(theme)) document.documentElement.style.setProperty(key, value); });
  await page.getByRole('tab', { name: 'Starter', exact: true }).click(); await page.locator('#name').fill('payment-service'); await page.locator('#create').click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'starter', stack: 'JAVA', name: 'payment-service' });
  await page.getByRole('tab', { name: 'Quality', exact: true }).click(); await page.getByRole('button', { name: 'Analyser le commit' }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'scan', source: 'SONAR' });
  await page.getByRole('tab', { name: 'Security', exact: true }).click(); await page.locator('#buildNumber').fill('42'); await page.getByRole('button', { name: 'Actualiser le rapport' }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'report', source: 'XRAY', buildNumber: '42' });
  await page.getByRole('tab', { name: 'GitFlow', exact: true }).click(); await page.getByRole('button', { name: 'Créer une MR', exact: true }).click();
  expect(await page.evaluate(() => window.sent.at(-1))).toEqual({ type: 'mr' });
  await page.getByRole('tab', { name: 'IA', exact: true }).click(); await page.screenshot({ path: path.join(__dirname, '../../.artifacts/interface-dark.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
