import * as vscode from 'vscode';
import { spawn, execFile, ChildProcessWithoutNullStreams } from 'node:child_process';
import { randomBytes, createHash } from 'node:crypto';
import { readFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createInterface } from 'node:readline';
import { repositoryPath } from './protocol';

/** Describes the administrator-owned package, never sent to the webview. Exigences : DEVAI-167, DEVAI-176. */
export interface Deployment {
  schemaVersion: number;
  profileId: string;
  environment: Record<string, string>;
  platform: string;
  pythonVersion: string;
}

/** Loads build-time configuration without accepting workspace settings or environment overrides. Exigences : DEVAI-167. */
export function deployment(extensionPath: string): Deployment {
  const file = join(extensionPath, 'runtime', 'deployment.json');
  const bytes = readFileSync(file), expected = readFileSync(file + '.sha256', 'utf8').trim();
  if (createHash('sha256').update(bytes).digest('hex') !== expected) throw new Error('Configuration embarquée altérée ; réinstaller le VSIX administré');
  const result: Deployment = JSON.parse(bytes.toString('utf8'));
  if (result.schemaVersion !== 1 || !result.environment || Object.values(result.environment).some(value => typeof value !== 'string')) throw new Error('Configuration embarquée invalide');
  if (result.platform !== `${process.platform}-${process.arch}`) throw new Error('Installer le VSIX compilé pour le système et l’architecture de ce poste');
  return result;
}

/** Removes configured secrets and private paths before native diagnostics reach the UI. Exigences : DEVAI-176. */
export function redact(text: string, environment: Record<string, string>): string {
  let result = text;
  const values = Object.entries(environment).filter(([key, value]) => /TOKEN|PASSWORD|KEY|SECRET/.test(key) && value && !value.startsWith('<')).map(([, value]) => value);
  for (const value of values.sort((a, b) => b.length - a.length)) result = result.split(value).join('<REDACTED>');
  for (const key of ['ODS_JFROG_PASSWORD', 'ODS_JFROG_TOKEN', 'ODS_SONAR_TOKEN']) {
    const user = environment[key.startsWith('ODS_JFROG') ? 'ODS_JFROG_USERNAME' : 'ODS_SONAR_USERNAME'];
    if (user && environment[key]) result = result.split(Buffer.from(`${user}:${environment[key]}`).toString('base64')).join('<REDACTED>');
  }
  return result.replace(/https?:\/\/[^\s/@]+:[^\s/@]+@/g, 'https://<REDACTED>@').replace(/[A-Z]:[\\/]+Users[\\/]+[^\r\n"<>]+/gi, '<USER_PATH>').replace(/\/home\/[^\s"<>]+/g, '<USER_PATH>');
}

/** Verifies developer access with the IDE's existing Git authentication before gateway startup. Exigences : DEVAI-169. */
export class GitAccess {
  /** Excludes organisation credentials from Git while retaining its existing credential-helper and SSH environment. Exigences : DEVAI-169, DEVAI-176. */
  private environment(): NodeJS.ProcessEnv { return Object.fromEntries(Object.entries({ ...process.env, GIT_TERMINAL_PROMPT: '0', GCM_INTERACTIVE: 'never' }).filter(([key]) => !/^(ODS_|MISTRAL_|VIBE_|SPRING_)/.test(key))); }
  /** Checks a trusted root, the pinned GitLab host, and an actual read of the repository. Exigences : DEVAI-016, DEVAI-169. */
  public async verify(root: vscode.WorkspaceFolder, configuredUrl: string): Promise<{ path: string; remote: string }> {
    if (!vscode.workspace.isTrusted) throw new Error('Approuver le dossier VS Code avant la connexion Git');
    const remote = (await this.command(root.uri.fsPath, ['remote', 'get-url', 'origin'])).trim();
    const expected = new URL(configuredUrl);
    const actual = remote.includes('://') ? new URL(remote).hostname : /^[^@/]+@([^:]+):/.exec(remote)?.[1];
    if (expected.protocol !== 'https:' || expected.username || expected.password || !actual || actual.toLowerCase() !== expected.hostname.toLowerCase()) throw new Error('Le dépôt doit appartenir au GitLab administré');
    const path = repositoryPath(remote);
    await this.command(root.uri.fsPath, ['ls-remote', '--', remote, 'HEAD']);
    return { path, remote };
  }
  /** Runs bounded read-only Git commands without prompts or service credentials. Exigences : DEVAI-169, DEVAI-176. */
  private command(cwd: string, args: string[]): Promise<string> {
    return new Promise((resolve, reject) => {
      execFile('git', args, { cwd, windowsHide: true, timeout: 30000, maxBuffer: 1048576, env: this.environment() }, (error, stdout) => {
        if (error) reject(new Error('Accès Git refusé ou indisponible. Connecter votre compte dans Git ou l’IDE, puis actualiser.'));
        else resolve(stdout);
      });
    });
  }
  /** Retrieves only an existing developer credential; it never prompts for a service account. Exigences : DEVAI-023, DEVAI-169. */
  public async token(root: vscode.WorkspaceFolder, gitlabUrl: string): Promise<string | undefined> {
    try {
      const session = await vscode.authentication.getSession('gitlab', ['api'], { silent: true });
      if (session?.accessToken) return session.accessToken;
    } catch { /* Git credential helpers remain usable when no GitLab authentication provider is installed. */ }
    return new Promise(resolve => {
      const child = spawn('git', ['credential', 'fill'], { cwd: root.uri.fsPath, windowsHide: true, env: this.environment(), stdio: 'pipe' });
      let output = ''; const timer = setTimeout(() => { child.kill(); resolve(undefined); }, 10000);
      child.stdout.on('data', data => { output += data.toString(); if (output.length > 65536) child.kill(); });
      child.stderr.on('data', () => {});
      child.once('error', () => { clearTimeout(timer); resolve(undefined); });
      child.once('close', code => { clearTimeout(timer); resolve(code === 0 ? /^password=(.+)$/m.exec(output)?.[1].trim() : undefined); });
      child.stdin.end(`url=${gitlabUrl.replace(/\/$/, '')}\n\n`);
    });
  }
}

/** Owns the packaged gateway, its private launch token and lifetime. Exigences : DEVAI-168, DEVAI-176. */
export class ManagedGateway implements vscode.Disposable {
  private child?: ChildProcessWithoutNullStreams;
  private starting?: Promise<void>;
  private endpoint?: URL;
  private credential = '';
  private disposed = false;
  /** Binds process resources and persistent local state to this installed extension. Exigences : DEVAI-168. */
  public constructor(private readonly context: vscode.ExtensionContext, private readonly config: Deployment, private readonly root: vscode.WorkspaceFolder, private readonly repository: string) {}
  /** Returns only a running loopback endpoint; services cannot be redirected by settings. Exigences : DEVAI-167, DEVAI-168. */
  public base(): URL { if (!this.endpoint) throw new Error('Vérification Git et démarrage de la gateway requis'); return this.endpoint; }
  /** Supplies the launch credential only to the native HTTP boundary. Exigences : DEVAI-168, DEVAI-176. */
  public token(): string { if (!this.credential || !this.endpoint) throw new Error('Gateway arrêtée'); return this.credential; }
  /** Starts a single owned process and waits for its actual HTTP listener to become ready. Exigences : DEVAI-168. */
  public async start(): Promise<void> {
    if (this.disposed) throw new Error('Gateway arrêtée');
    if (this.endpoint) return;
    if (this.starting) return this.starting;
    this.starting = this.launch().finally(() => { this.starting = undefined; });
    return this.starting;
  }
  /** Creates an isolated launch environment and consumes only the bounded readiness signal. Exigences : DEVAI-168, DEVAI-176. */
  private async launch(): Promise<void> {
    const runtime = join(this.context.extensionPath, 'runtime'), jar = join(runtime, 'ods-gateway.jar');
    const expected = readFileSync(jar + '.sha256', 'utf8').trim();
    if (createHash('sha256').update(readFileSync(jar)).digest('hex') !== expected) throw new Error('Gateway embarquée altérée');
    const rootHash = createHash('sha256').update(this.config.profileId + '\0' + this.root.uri.toString()).digest('hex');
    const storage = join(this.context.globalStorageUri.fsPath, rootHash); mkdirSync(storage, { recursive: true });
    let dataKey = this.config.environment.ODS_DATA_KEY_BASE64 || await this.context.secrets.get('ods.localDataKey');
    if (!dataKey) { dataKey = randomBytes(32).toString('base64'); await this.context.secrets.store('ods.localDataKey', dataKey); }
    if (this.disposed) throw new Error('Démarrage de la gateway annulé');
    this.credential = randomBytes(32).toString('hex');
    const env: NodeJS.ProcessEnv = {};
    for (const key of ['PATH', 'Path', 'SystemRoot', 'SYSTEMROOT', 'WINDIR', 'TEMP', 'TMP', 'HOME', 'USERPROFILE', 'LANG']) if (process.env[key]) env[key] = process.env[key];
    Object.assign(env, this.config.environment, { ODS_EMBEDDED: 'true', ODS_LOCAL_TOKEN: this.credential, ODS_BIND_ADDRESS: '127.0.0.1', ODS_GATEWAY_PORT: '0', ODS_RUNTIME_DIRECTORY: runtime, ODS_WORKSPACE: this.root.uri.fsPath, ODS_REPOSITORY: this.repository, ODS_DATA_KEY_BASE64: dataKey, ODS_DATABASE_URL: 'jdbc:h2:file:./gateway;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE', SPRING_PROFILES_ACTIVE: 'local', SPRING_CONFIG_ADDITIONAL_LOCATION: 'optional:' + pathToFileURL(join(runtime, 'config', 'application-organisation.properties')).href });
    env.ODS_PYTHON_COMMAND = process.platform === 'win32' ? 'py' : 'python3.12';
    const java = vscode.workspace.getConfiguration('ods').get<string>('javaPath', 'java');
    const child = spawn(java, ['-jar', jar, '--server.address=127.0.0.1', '--server.port=0', '--ods.embedded=true', '--spring.profiles.active=local', '--spring.datasource.url=jdbc:h2:file:./gateway;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE'], { cwd: storage, env, windowsHide: true, stdio: 'pipe' }); this.child = child;
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => { this.dispose(); reject(new Error('Délai de démarrage de la gateway dépassé ; vérifier Java 21')); }, 60000);
      child.stderr.on('data', () => { /* JVM and service output is not forwarded to extension logs. */ });
      child.once('error', () => { clearTimeout(timer); this.dispose(); reject(new Error('Impossible de démarrer la gateway ; Java 21 est requis')); });
      child.once('exit', () => { clearTimeout(timer); if (this.child === child) { this.child = undefined; this.endpoint = undefined; this.credential = ''; } reject(new Error('La gateway s’est arrêtée')); });
      createInterface({ input: child.stdout }).on('line', line => {
        const match = /^ODS_READY:([0-9]{1,5})$/.exec(line);
        if (match && this.child === child && Number(match[1]) > 0 && Number(match[1]) < 65536) { clearTimeout(timer); this.endpoint = new URL(`http://127.0.0.1:${match[1]}`); resolve(); }
      });
    });
  }
  /** Closes the parent channel and terminates only owned descendants if graceful shutdown stalls. Exigences : DEVAI-168. */
  public dispose(): void {
    this.disposed = true;
    const child = this.child; this.child = undefined; this.endpoint = undefined; this.credential = '';
    if (!child) return; child.stdin.end();
    const timer = setTimeout(() => {
      if (child.exitCode !== null) return;
      if (process.platform === 'win32' && child.pid) spawn('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' }).unref();
      else child.kill('SIGTERM');
    }, 4000);
    timer.unref(); child.once('exit', () => clearTimeout(timer));
  }
}
