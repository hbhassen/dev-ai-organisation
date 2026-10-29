// Browser validation of the real webview markup, scripts and styles. DEVAI-019, DEVAI-135.
const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({ testDir: './test', testMatch: 'ui.spec.cjs', outputDir: '../.artifacts/ui-results', forbidOnly: true, retries: 0, workers: 1, timeout: 30000, reporter: 'list', use: { browserName: 'chromium', screenshot: 'only-on-failure' } });
