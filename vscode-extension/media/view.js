/* Presentation only. All filesystem, process and network effects use the native host. Exigences : DEVAI-002, DEVAI-018. */
const vscode = acquireVsCodeApi();
/** Resolves a static DOM element without evaluating external selectors. Exigences : DEVAI-018. */
function element(id) { return document.getElementById(id); }
/** Posts a typed UI action without tokens, paths or native permissions. Exigences : DEVAI-018. */
function send(message) { vscode.postMessage(message); }
/** Appends untrusted text using textContent, never HTML interpolation. Exigences : DEVAI-018, DEVAI-151. */
function entry(role, text) {
  const article = document.createElement('article'); article.className = `entry ${role}`;
  const label = document.createElement('strong'); label.textContent = { user: 'Vous', assistant: 'Mistral', system: 'ODS', tool: 'Outil' }[role] || 'ODS';
  const content = document.createElement('pre'); content.textContent = text;
  article.append(label, content); element('conversation').append(article); article.scrollIntoView({ block: 'nearest' });
}
document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => {
  document.querySelectorAll('.panel').forEach(panel => { panel.hidden = panel.id !== button.dataset.tab; });
  document.querySelectorAll('[data-tab]').forEach(tab => tab.setAttribute('aria-selected', String(tab === button)));
}));
document.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => send({ type: button.dataset.action })));
document.querySelectorAll('[data-git]').forEach(button => button.addEventListener('click', () => send({ type: 'git', action: button.dataset.git })));
document.querySelectorAll('[data-scan]').forEach(button => button.addEventListener('click', () => send({ type: 'scan', source: button.dataset.scan })));
document.querySelectorAll('[data-report]').forEach(button => button.addEventListener('click', () => send({ type: 'report', source: button.dataset.report, buildNumber: element('buildNumber').value })));
document.querySelectorAll('[data-remediate]').forEach(button => button.addEventListener('click', () => send({ type: 'remediate', source: button.dataset.remediate })));
element('create').addEventListener('click', () => send({ type: 'starter', stack: element('stack').value, name: element('name').value }));
element('send').addEventListener('click', () => send({ type: 'chat', mode: element('mode').value, task: element('task').value, message: element('message').value, includeActive: element('includeActive').checked }));
window.addEventListener('message', event => {
  const m = event.data;
  switch (m.type) {
    case 'context': element('context').textContent = `${m.name || 'Projet'} · ${m.branch || 'Sans branche'} · ${(m.stacks || []).join(' / ') || 'Stack à détecter'}`; break;
    case 'capabilities': element('capabilities').textContent = m.rule?.aiEnabled ? `IA autorisée · Mistral · ${m.rule.modelAlias} · politique ${m.policyVersion}` : `IA non disponible · ${m.reason || 'Projet non autorisé'}`; break;
    case 'status': element('status').textContent = m.message; break;
    case 'error': element('status').textContent = m.message; entry('system', m.message); break;
    case 'info': entry('system', m.message); element('status').textContent = 'Opération terminée ; consulter le résultat'; break;
    case 'clearChat': element('conversation').replaceChildren(); break;
    case 'chat': element('streaming')?.remove(); entry(m.role, m.text); break;
    case 'progress': {
      let partial = element('streaming');
      if (!partial) { partial = document.createElement('pre'); partial.id = 'streaming'; partial.className = 'entry assistant'; element('conversation').append(partial); }
      partial.textContent = m.text; partial.scrollIntoView({ block: 'nearest' }); break;
    }
    case 'tasks': {
      const selected = element('task').value; element('task').replaceChildren();
      for (const task of m.tasks || []) { const option = document.createElement('option'); option.value = task.id; option.textContent = task.label; element('task').append(option); }
      if ([...element('task').options].some(option => option.value === selected)) element('task').value = selected;
      break;
    }
    case 'tool': entry('tool', `${m.name}\n${JSON.stringify(m.arguments, null, 2)}`); break;
    case 'plan': entry('system', m.steps.map((step, index) => `${index + 1}. [${step.status}] ${step.title}`).join('\n')); break;
    case 'process': element('status').textContent = m.running ? `Commande en cours… ${m.output.slice(-150)}` : `Commande terminée : code ${m.exitCode}`; if (!m.running) entry('tool', `Code retour : ${m.exitCode}\n${m.output}`); break;
    case 'usage': element('usage').textContent = Number.isInteger(m.prompt_tokens) ? `${m.prompt_tokens} tokens d'entrée · ${m.completion_tokens} de sortie · ${m.total_tokens} au total` : 'Consommation en attente de mesure'; break;
    case 'provenance': element('capabilities').textContent = `Mistral · recommandations ${m.origin} · empreinte ${String(m.hash).slice(0, 12)}`; break;
    case 'report': element(m.source === 'SONAR' ? 'sonar' : 'xray').textContent = JSON.stringify(m.data, null, 2); break;
    case 'git': element('git-output').textContent = JSON.stringify(m.data, null, 2); break;
  }
});
