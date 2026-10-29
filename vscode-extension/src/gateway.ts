import * as vscode from 'vscode';
import { randomBytes, createHash, randomUUID } from 'node:crypto';

/** Authenticated gateway client; provider credentials never enter the extension. Exigences : DEVAI-020, DEVAI-021, DEVAI-035. */
export class GatewayClient implements vscode.UriHandler {
  private authorization?: { state: string; verifier: string; redirect: string; tokenEndpoint: string; clientId: string; base: string; created: number };
  private refreshing?: Promise<string | undefined>;
  /** Stores only user authorization in native SecretStorage. Exigences : DEVAI-021. */
  public constructor(private readonly context: vscode.ExtensionContext) {}
  /** Resolves the configured gateway and permits HTTP only on explicit loopback hosts. Exigences : DEVAI-036. */
  public base(): URL {
    const url = new URL(vscode.workspace.getConfiguration('ods').get<string>('gatewayUrl', 'http://127.0.0.1:8080'));
    if (url.username || url.password || (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)))) throw new Error('Gateway HTTPS requise hors loopback');
    return url;
  }
  /** Binds stored credentials to one gateway so changing configuration never forwards an old session. Exigences : DEVAI-021, DEVAI-036. */
  private secret(name: string): string { return `ods.${createHash('sha256').update(this.base().origin).digest('hex')}.${name}`; }
  /** Refreshes an expired access token once, retaining refresh credentials only in SecretStorage. Exigences : DEVAI-021. */
  private async access(): Promise<string | undefined> {
    const stored = await this.context.secrets.get(this.secret('session'));
    if (!stored) return undefined;
    const session = JSON.parse(stored);
    if (Date.now() < session.expiresAt - 30000) return session.accessToken;
    if (!session.refreshToken) throw new Error('Session expirée ; reconnecter le SSO');
    if (!this.refreshing) this.refreshing = this.refresh(session).finally(() => { this.refreshing = undefined; });
    return this.refreshing;
  }
  /** Rotates refresh tokens using the original verified OIDC endpoint and public client identity. Exigences : DEVAI-021. */
  private async refresh(session: any): Promise<string> {
    const response = await fetch(session.tokenEndpoint, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: session.refreshToken, client_id: session.clientId }), redirect: 'error', signal: AbortSignal.timeout(30000) });
    const tokens: any = await response.json();
    if (!response.ok || typeof tokens.access_token !== 'string') { await this.context.secrets.delete(this.secret('session')); throw new Error('Session expirée ou révoquée ; reconnecter le SSO'); }
    await this.context.secrets.store(this.secret('session'), JSON.stringify({ ...session, accessToken: tokens.access_token, refreshToken: tokens.refresh_token ?? session.refreshToken, expiresAt: Date.now() + Math.max(1, Number(tokens.expires_in ?? 300)) * 1000 }));
    return tokens.access_token;
  }
  /** Calls one internal API route with a fresh idempotency key and bounded network timeout. Exigences : DEVAI-077, DEVAI-117. */
  public async request(route: string, method = 'GET', body?: object, authenticated = true): Promise<any> {
    if (!route.startsWith('/api/v1/')) throw new Error('Route ODS invalide');
    const headers: Record<string, string> = { Accept: 'application/json', 'Content-Type': 'application/json' };
    if (authenticated) {
      const token = await this.access();
      if (!token) throw new Error('Connexion SSO requise');
      headers.Authorization = `Bearer ${token}`;
      const gitlab = await this.context.secrets.get(this.secret('gitlab')); if (gitlab) headers['X-ODS-GitLab-Token'] = gitlab;
    }
    if (method === 'POST') headers['Idempotency-Key'] = randomUUID();
    const result = await fetch(new URL(route, this.base()), { method, headers, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(150000), redirect: 'error' });
    const text = await result.text(); if (text.length > 8_388_608) throw new Error('Réponse trop volumineuse');
    let data: any; try { data = text ? JSON.parse(text) : {}; } catch { throw new Error(`Réponse HTTP invalide (${result.status})`); }
    if (!result.ok) throw new Error(`${data.code ?? result.status} : ${data.message ?? 'Vérifier identité et configuration'}`);
    return data;
  }
  /** Reads live text events with bearer authentication; interrupted streams fall back to authoritative job polling. Exigences : DEVAI-078. */
  public async progress(job: string, consume: (text: string) => void, signal: AbortSignal): Promise<void> {
    const token = await this.access(); if (!token) return;
    const response = await fetch(new URL(`/api/v1/jobs/${encodeURIComponent(job)}/events`, this.base()), { headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` }, signal, redirect: 'error' });
    if (!response.ok || !response.body) return;
    const reader = response.body.getReader(), decoder = new TextDecoder(); let buffer = '';
    try {
      for (;;) {
        const part = await reader.read(); if (part.done) break; buffer += decoder.decode(part.value, { stream: true });
        if (buffer.length > 262144) throw new Error('Flux trop volumineux');
        let end: number;
        while ((end = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, end).trim(); buffer = buffer.slice(end + 1);
          if (line.startsWith('data:')) { const data = JSON.parse(line.slice(5).trim()); if (typeof data.text === 'string') consume(data.text); }
        }
      }
    } finally { reader.releaseLock(); }
  }
  /** Begins OIDC Authorization Code with PKCE through the system browser. Exigences : DEVAI-020. */
  public async signIn(): Promise<void> {
    const config = await this.request('/api/v1/auth/config', 'GET', undefined, false);
    if (!config.issuer || !config.issuer.startsWith('https:') || config.issuer.includes('.example')) throw new Error('Configurer ODS_OIDC_ISSUER dans la gateway avant la connexion');
    const metadataResponse = await fetch(`${config.issuer.replace(/\/$/, '')}/.well-known/openid-configuration`, { redirect: 'error', signal: AbortSignal.timeout(15000) });
    if (!metadataResponse.ok) throw new Error('Métadonnées OIDC indisponibles');
    const metadata: any = await metadataResponse.json();
    if (metadata.issuer !== config.issuer || !metadata.authorization_endpoint?.startsWith('https:') || !metadata.token_endpoint?.startsWith('https:')) throw new Error('Métadonnées OIDC incohérentes');
    const redirect = (await vscode.env.asExternalUri(vscode.Uri.parse(`${vscode.env.uriScheme}://${this.context.extension.id}/callback`))).toString();
    const verifier = randomBytes(32).toString('base64url'), state = randomBytes(24).toString('base64url');
    this.authorization = { state, verifier, redirect, tokenEndpoint: metadata.token_endpoint, clientId: config.clientId, base: this.base().origin, created: Date.now() };
    const url = new URL(metadata.authorization_endpoint);
    for (const [key, value] of Object.entries({ response_type: 'code', client_id: config.clientId, redirect_uri: redirect, scope: 'openid profile', state, code_challenge: createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256' })) url.searchParams.set(key, String(value));
    await vscode.env.openExternal(vscode.Uri.parse(url.toString()));
  }
  /** Exchanges only the matching PKCE callback; credentials are never posted to a webview. Exigences : DEVAI-020, DEVAI-021. */
  public async handleUri(uri: vscode.Uri): Promise<void> {
    const values = new URLSearchParams(uri.query), pending = this.authorization;
    this.authorization = undefined;
    if (!pending || Date.now() - pending.created > 300000 || pending.base !== this.base().origin || uri.path !== '/callback' || values.get('state') !== pending.state || !values.get('code')) { void vscode.window.showErrorMessage('Réponse OIDC invalide'); return; }
    const result = await fetch(pending.tokenEndpoint, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'authorization_code', code: values.get('code')!, client_id: pending.clientId, code_verifier: pending.verifier, redirect_uri: pending.redirect }), redirect: 'error', signal: AbortSignal.timeout(30000) });
    const tokens: any = await result.json(); if (!result.ok || typeof tokens.access_token !== 'string') throw new Error('Échec OIDC');
    await this.context.secrets.store(this.secret('session'), JSON.stringify({ accessToken: tokens.access_token, refreshToken: tokens.refresh_token, expiresAt: Date.now() + Math.max(1, Number(tokens.expires_in ?? 300)) * 1000, tokenEndpoint: pending.tokenEndpoint, clientId: pending.clientId }));
    void vscode.window.showInformationMessage('ODS : connexion SSO établie');
  }
  /** Associates a developer-owned GitLab token, independently verified against the OIDC subject by the gateway. Exigences : DEVAI-023. */
  public async connectGitlab(): Promise<void> {
    const value = await vscode.window.showInputBox({ title: 'Autorisation GitLab utilisateur', prompt: 'Jeton de votre compte GitLab ; stocké uniquement dans SecretStorage. Aucun compte de service.', password: true, ignoreFocusOut: true });
    if (value) await this.context.secrets.store(this.secret('gitlab'), value);
  }
  /** Erases local user sessions and pending authorization state. Exigences : DEVAI-021, DEVAI-024. */
  public async signOut(): Promise<void> { this.authorization = undefined; await this.context.secrets.delete(this.secret('session')); await this.context.secrets.delete(this.secret('gitlab')); }
}
