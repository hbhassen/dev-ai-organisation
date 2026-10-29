/** Parses a GitLab clone URL without exposing credentials or trusting its host as authorization. Exigences : DEVAI-025. */
export function repositoryPath(remote: string): string {
  let path: string;
  if (/^[^@/]+@[^:]+:/.test(remote)) path = remote.slice(remote.indexOf(':') + 1);
  else { const url = new URL(remote); if (!['https:', 'ssh:'].includes(url.protocol) || url.password) throw new Error('Remote non pris en charge'); path = url.pathname.replace(/^\//, ''); }
  path = path.replace(/\.git$/, '');
  if (!path || path.includes('..')) throw new Error('Chemin GitLab invalide');
  return path;
}
/** Checks that a webview command is one of the fixed native operations. Exigences : DEVAI-018. */
export function validateMessage(message: unknown): Record<string, any> {
  if (!message || typeof message !== 'object' || Array.isArray(message)) throw new Error('Message invalide');
  const input = message as Record<string, any>;
  if (!['attach', 'detach', 'refresh', 'root', 'signOut', 'sbom', 'downloadSbom', 'starter', 'chat', 'resume', 'newChat', 'stop', 'prompts', 'scan', 'report', 'remediate', 'git', 'mr', 'mrDescription', 'changelog', 'releaseNotes', 'restore', 'deleteSession'].includes(input.type)) throw new Error('Commande inconnue');
  if (JSON.stringify(input).length > 100000) throw new Error('Message trop volumineux');
  return input;
}
