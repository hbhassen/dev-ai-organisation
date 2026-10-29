import * as vscode from 'vscode';
import { randomUUID } from 'node:crypto';
import { Deployment, GitAccess, ManagedGateway, deployment, redact } from './managed';

/** Connects to the owned gateway only after verifying existing developer Git access. Exigences : DEVAI-168, DEVAI-169. */
export class GatewayClient implements vscode.Disposable {
  private config?: Deployment;
  private runtime?: ManagedGateway;
  private root?: vscode.WorkspaceFolder;
  private repository?: string;
  private generation = 0;
  private readonly git = new GitAccess();
  /** Keeps service configuration in the native host, outside all editable webview state. Exigences : DEVAI-167, DEVAI-176. */
  public constructor(private readonly context: vscode.ExtensionContext) {}
  /** Revalidates repository access on refresh and starts the gateway after successful Git verification. Exigences : DEVAI-169. */
  public async connect(root: vscode.WorkspaceFolder): Promise<void> {
    const generation = this.generation;
    this.config ??= deployment(this.context.extensionPath);
    let verified: { path: string; remote: string };
    try { verified = await this.git.verify(root, this.config.environment.ODS_GITLAB_URL); }
    catch (error) { this.dispose(); throw error; }
    if (generation !== this.generation) throw new Error('Connexion annulée pendant la vérification Git');
    if (this.root && (this.root.uri.toString() !== root.uri.toString() || this.repository !== verified.path)) this.dispose();
    this.root = root; this.repository = verified.path; this.runtime ??= new ManagedGateway(this.context, this.config, root, verified.path);
    try { await this.runtime.start(); } catch (error) { this.dispose(); throw error; }
  }
  /** Returns the actual private listener rather than a developer-editable endpoint. Exigences : DEVAI-167, DEVAI-168. */
  public base(): URL { if (!this.runtime) throw new Error('Accès Git vérifié requis avant la connexion'); return this.runtime.base(); }
  /** Applies exact credential masking to native errors and presentation payloads. Exigences : DEVAI-176. */
  public safe(text: string): string { return redact(text, this.config?.environment ?? {}); }
  /** Sends bounded local requests; only MR publication receives an existing user API credential. Exigences : DEVAI-023, DEVAI-117, DEVAI-169. */
  public async request(route: string, method = 'GET', body?: object): Promise<any> {
    if (!route.startsWith('/api/v1/') || !this.runtime || !this.root) throw new Error('Accès Git vérifié et gateway active requis');
    const headers: Record<string, string> = { Accept: 'application/json', 'Content-Type': 'application/json', 'X-ODS-Local-Token': this.runtime.token() };
    if (route === '/api/v1/git/merge-requests') {
      const token = await this.git.token(this.root, this.config!.environment.ODS_GITLAB_URL);
      if (!token) throw new Error('La MR nécessite votre jeton utilisateur disponible via GitLab dans l’IDE ou Git Credential Manager. Vos opérations Git SSH restent disponibles.');
      headers['X-ODS-GitLab-Token'] = token;
    }
    if (method === 'POST') headers['Idempotency-Key'] = randomUUID();
    const result = await fetch(new URL(route, this.base()), { method, headers, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(150000), redirect: 'error' });
    const text = await result.text(); if (text.length > 8_388_608) throw new Error('Réponse trop volumineuse');
    let data: any; try { data = text ? JSON.parse(text, (_key, value) => typeof value === 'string' ? this.safe(value) : value) : {}; } catch { throw new Error(`Réponse HTTP invalide (${result.status})`); }
    if (!result.ok) throw new Error(`${data.code ?? result.status} : ${data.message ?? 'Vérifier la configuration administrée'}`);
    return data;
  }
  /** Streams presentation text over the owned session while retaining authoritative job polling. Exigences : DEVAI-078, DEVAI-168. */
  public async progress(job: string, consume: (text: string) => void, signal: AbortSignal): Promise<void> {
    if (!this.runtime) return;
    const response = await fetch(new URL(`/api/v1/jobs/${encodeURIComponent(job)}/events`, this.base()), { headers: { Accept: 'text/event-stream', 'X-ODS-Local-Token': this.runtime.token() }, signal, redirect: 'error' });
    if (!response.ok || !response.body) return;
    const reader = response.body.getReader(), decoder = new TextDecoder(); let buffer = '';
    try {
      for (;;) {
        const part = await reader.read(); if (part.done) break; buffer += decoder.decode(part.value, { stream: true });
        if (buffer.length > 262144) throw new Error('Flux trop volumineux');
        let end: number;
        while ((end = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, end).trim(); buffer = buffer.slice(end + 1);
          if (line.startsWith('data:')) { const data = JSON.parse(line.slice(5).trim()); if (typeof data.text === 'string') consume(this.safe(data.text)); }
        }
      }
    } finally { reader.releaseLock(); }
  }
  /** Stops the owned gateway without deleting the developer's existing Git credentials. Exigences : DEVAI-168, DEVAI-169. */
  public async signOut(): Promise<void> { this.dispose(); }
  /** Releases the local process when its extension or workspace is closed. Exigences : DEVAI-168. */
  public dispose(): void { this.generation++; this.runtime?.dispose(); this.runtime = undefined; this.root = undefined; }
}
