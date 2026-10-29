const assert = require('node:assert/strict');
const vscode = require('vscode');
const path = require('node:path');
/** Activates the real extension host and exercises IPC against the bundled Java executable. Exigences : DEVAI-014, DEVAI-016, DEVAI-145. */
async function run() {
  const extension = vscode.extensions.getExtension('ods-internal.organisation-development-standard');
  assert.ok(extension, 'Installed/development extension found');
  await extension.activate();
  assert.ok((await vscode.commands.getCommands(true)).includes('ods.open'));
  const { EngineClient } = require(path.join(extension.extensionPath, 'dist/engine.js'));
  const engine = new EngineClient(extension.extensionPath);
  try {
    await engine.start();
    const root = vscode.workspace.workspaceFolders[0]; assert.ok(root);
    await engine.open(root);
    const inspection = await engine.call('project.inspect'); assert.ok(inspection.stacks.includes('PYTHON'));
    const content = await engine.call('files.read', { path: 'pyproject.toml' }); assert.equal(content.sha256.length, 64);
    await assert.rejects(engine.call('files.read', { path: '../escape' }));
    await vscode.commands.executeCommand('ods.open');
    console.log('ODS extension-host smoke: activation, commands, bundled Java IPC, workspace inspection and path isolation passed.');
  } finally { engine.dispose(); }
}
exports.run = run;
