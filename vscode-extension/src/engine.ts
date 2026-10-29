import * as vscode from 'vscode';
import { spawn, ChildProcessWithoutNullStreams } from 'node:child_process';
import { createInterface } from 'node:readline';
import { join } from 'node:path';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

/** Owns one Java subprocess and correlates bounded JSON-RPC requests. Exigences : DEVAI-014, DEVAI-015. */
export class EngineClient implements vscode.Disposable {
  private child?: ChildProcessWithoutNullStreams;
  private sequence = 0;
  private pending = new Map<number, { resolve: (value: any) => void; reject: (error: Error) => void; timer: NodeJS.Timeout }>();
  /** Binds the engine to installed extension resources rather than the current directory. Exigences : DEVAI-141. */
  public constructor(private readonly extensionPath: string) {}
  /** Starts Java, reserves stdout for IPC and verifies protocol compatibility. Exigences : DEVAI-014, DEVAI-015. */
  public async start(): Promise<void> {
    if (this.child) return;
    const executable = vscode.workspace.getConfiguration('ods').get<string>('javaPath', 'java');
    const jar = join(this.extensionPath, 'engine', 'ods-java-core.jar');
    const expected = readFileSync(jar + '.sha256', 'utf8').trim();
    if (!/^[a-f0-9]{64}$/.test(expected) || createHash('sha256').update(readFileSync(jar)).digest('hex') !== expected) throw new Error('Moteur embarqué altéré ; réinstaller le VSIX approuvé');
    this.child = spawn(executable, ['-jar', jar], { stdio: 'pipe', windowsHide: true });
    const child = this.child;
    this.child.stderr.on('data', () => { /* Avoid exposing JVM environment or paths in published diagnostics. */ });
    this.child.on('error', () => { if (this.child === child) { this.child = undefined; this.fail('Java 21 introuvable ou moteur indisponible'); } });
    this.child.on('exit', () => { if (this.child === child) { this.child = undefined; this.fail('Le moteur Java est arrêté ; relancer la commande'); } });
    createInterface({ input: this.child.stdout }).on('line', line => {
      if (line.length > 8_388_608) { this.dispose(); return; }
      try {
        const message = JSON.parse(line); const request = this.pending.get(message.id);
        if (!request) return;
        clearTimeout(request.timer); this.pending.delete(message.id);
        if (message.error) request.reject(new Error(`${message.error.data?.code ?? 'IPC_ERROR'} : ${message.error.message}`));
        else request.resolve(message.result);
      } catch { this.fail('Réponse IPC invalide'); }
    });
    const result = await this.call('initialize', { protocolVersion: 1 });
    if (result.protocolVersion !== 1) throw new Error('Version du moteur incompatible');
  }
  /** Sends a typed request with a deadline; side effects are never automatically replayed. Exigences : DEVAI-014, DEVAI-015. */
  public call(method: string, params: object = {}): Promise<any> {
    if (!this.child) return Promise.reject(new Error('Moteur Java non démarré'));
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('Délai du moteur dépassé ; résultat à vérifier avant répétition')); }, ['starters.create', 'git.clone'].includes(method) ? 900000 : 30000);
      this.pending.set(id, { resolve, reject, timer });
      const line = JSON.stringify({ jsonrpc: '2.0', id, method, params });
      if (Buffer.byteLength(line) > 8_388_608) { clearTimeout(timer); this.pending.delete(id); reject(new Error('Message trop volumineux')); return; }
      this.child!.stdin.write(line + '\n');
    });
  }
  /** Opens one native root and forwards actual VS Code trust, not a webview-provided flag. Exigences : DEVAI-016, DEVAI-017. */
  public async open(root: vscode.WorkspaceFolder): Promise<void> { await this.start(); await this.call('workspace.open', { root: root.uri.fsPath, trusted: vscode.workspace.isTrusted }); }
  /** Rejects outstanding calls safely after a subprocess failure. Exigences : DEVAI-015. */
  private fail(message: string): void { for (const p of this.pending.values()) { clearTimeout(p.timer); p.reject(new Error(message)); } this.pending.clear(); }
  /** Stops the owned process when the extension/window is deactivated. Exigences : DEVAI-015. */
  public dispose(): void {
    const child = this.child; this.child = undefined; this.fail('Moteur fermé'); if (!child) return;
    child.stdin.end();
    const timer = setTimeout(() => {
      if (child.exitCode !== null) return;
      if (process.platform === 'win32' && child.pid) spawn('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' }).unref();
      else child.kill('SIGTERM');
    }, 1500);
    timer.unref(); child.once('exit', () => clearTimeout(timer));
  }
}
