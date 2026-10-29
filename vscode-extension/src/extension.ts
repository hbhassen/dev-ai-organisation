import * as vscode from 'vscode';
import { EngineClient } from './engine';
import { GatewayClient } from './gateway';
import { OdsView } from './view';

/** Registers the native views and identity boundary; business processing remains in Java. Exigences : DEVAI-002, DEVAI-013. */
export function activate(context: vscode.ExtensionContext): void {
  const engine = new EngineClient(context.extensionPath), gateway = new GatewayClient(context), view = new OdsView(context, engine, gateway);
  context.subscriptions.push(engine, vscode.window.registerUriHandler(gateway), vscode.workspace.registerTextDocumentContentProvider('ods-diff', view), vscode.window.registerWebviewViewProvider('ods.workspace', view, { webviewOptions: { retainContextWhenHidden: true } }));
  context.subscriptions.push(vscode.commands.registerCommand('ods.open', () => vscode.commands.executeCommand('workbench.view.extension.ods')));
  context.subscriptions.push(vscode.commands.registerCommand('ods.signIn', () => gateway.signIn()), vscode.commands.registerCommand('ods.gitlabToken', () => gateway.connectGitlab()), vscode.commands.registerCommand('ods.signOut', () => view.signOut()));
  context.subscriptions.push(vscode.workspace.onDidChangeWorkspaceFolders(() => { void view.workspaceChanged().catch(() => {}); }), vscode.workspace.onDidGrantWorkspaceTrust(() => { void view.refresh().catch(() => {}); }));
}
