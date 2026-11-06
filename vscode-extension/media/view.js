/* Presentation only. All filesystem, process and network effects use the native host. Exigences : DEVAI-002, DEVAI-018. */
const vscode = acquireVsCodeApi();
let busy = false, allowed = false, sonarPage = 1;
let documentActions = new Set();
let selectedPaths = [], modelCapabilities = {};
/** Resolves a static DOM element without evaluating external selectors. Exigences : DEVAI-018. */
function element(id) { return document.getElementById(id); }
/** Posts a typed UI action without tokens, paths or native permissions. Exigences : DEVAI-018. */
function send(message) { vscode.postMessage(message); }
/** Formats authoritative model metadata without substituting an alias or a default provider. Exigences : DEVAI-192, DEVAI-196. */
function modelLabel(data, fallback) {
  const provider = data?.provider, model = data?.model;
  const name = provider === 'OPENAI' ? 'OpenAI' : provider === 'MISTRAL' ? 'Mistral' : provider;
  return typeof name === 'string' && typeof model === 'string' && name && model ? `${name} · ${model}` : fallback;
}
/** Renders the signed per-project choice list as inert labels and retains an explicit revoked-selection state. Exigences : DEVAI-018, DEVAI-199. */
function renderModels(data) {
  const select = element('model-choice'); select.replaceChildren();
  const choices = allowed ? (Array.isArray(data.models) ? data.models : data.modelAlias ? [data] : []) : [];
  if (!choices.length || data.selectionRequired) { const option = document.createElement('option'); option.value = ''; option.textContent = choices.length ? 'Sélectionner un modèle autorisé' : 'Aucun modèle disponible'; option.disabled = true; select.append(option); }
  for (const model of choices) { const option = document.createElement('option'); option.value = model.modelAlias; option.textContent = modelLabel(model, 'Modèle non communiqué'); select.append(option); }
  select.value = data.selectionRequired ? '' : data.modelAlias || '';
}
/** Distinguishes pending file selection, local stack detection and effective read-tool availability. Exigences : DEVAI-065, DEVAI-193, DEVAI-195. */
function contextStatus() {
  const selected = selectedPaths.length ? `${selectedPaths.length} fichier(s) sélectionné(s), soumis à aperçu avant envoi.` : 'Aucun fichier sélectionné.';
  element('context-status').textContent = `${selected}${element('includeActive').checked ? ' Fichier actif demandé ; sa présence sera vérifiée avant envoi.' : ''} La détection des technologies ne transmet pas le code.`;
  const tools = !allowed ? 'Outils de lecture indisponibles.' : modelCapabilities.tools === false || modelCapabilities.rule?.contextMode === 'SERVER_VERIFIED' ? 'Outils de lecture désactivés : joignez les fichiers utiles pour une question sur le projet.' : modelCapabilities.tools === true ? 'Outils de lecture disponibles après votre autorisation.' : 'Disponibilité des outils de lecture non communiquée.';
  element('model-capabilities').textContent = tools + (allowed && Number.isInteger(modelCapabilities.maxOutputTokens) ? ` Réponse limitée à ${modelCapabilities.maxOutputTokens} tokens.` : '');
}
/** Appends inert text and preserves each response's own provider/model across policy refreshes. Exigences : DEVAI-018, DEVAI-151, DEVAI-192. */
function entry(role, text, identity) {
  element('welcome')?.remove();
  const article = document.createElement('article'); article.className = `entry ${role}`;
  const label = document.createElement('strong'); label.textContent = role === 'assistant' ? modelLabel(identity, 'Assistant · modèle non communiqué') : { user: 'Vous', system: 'ODS', tool: 'Outil' }[role] || 'ODS';
  const content = document.createElement('pre'); content.textContent = text;
  article.append(label, content); element('conversation').append(article); article.scrollIntoView({ block: 'nearest' });
}
/** Keeps send, mode and stop controls consistent with the actual host task state. Exigences : DEVAI-019, DEVAI-151, DEVAI-180, DEVAI-181. */
function composerState() { document.querySelectorAll('[data-correction-stop]').forEach(button => { button.hidden = !busy; }); document.querySelectorAll('[data-attempts]').forEach(input => { input.disabled = busy; }); element('model-choice').disabled = busy || !allowed || element('model-choice').options.length < 2; for (const option of element('mode').options) option.disabled = option.value !== 'QUESTION' && modelCapabilities.tools === false; if (modelCapabilities.tools === false && element('mode').value !== 'QUESTION') element('mode').value = 'QUESTION'; element('send').disabled = busy || !allowed || !!modelCapabilities.selectionRequired || !element('message').value.trim(); element('stop').hidden = !busy; element('mode').disabled = busy; element('task').disabled = busy; element('attach').disabled = busy; document.querySelectorAll('[data-policy-action]').forEach(button => { button.disabled = busy || !allowed || !documentActions.has(button.dataset.policyAction); }); document.querySelectorAll('[data-scan], [data-remediate], [data-coverage]').forEach(button => { button.disabled = busy || ((button.hasAttribute('data-remediate') || button.hasAttribute('data-coverage')) && !allowed); }); }
/** Submits one draft while retaining its text until the native host acknowledges acceptance. Exigences : DEVAI-063, DEVAI-077. */
function submit() { if (busy || element('send').disabled) return; busy = true; composerState(); send({ type: 'chat', mode: element('mode').value, task: element('task').value, message: element('message').value, includeActive: element('includeActive').checked }); }
/** Uses one roving tab stop and standard arrow navigation for the five panels. Exigences : DEVAI-013, DEVAI-019. */
function selectTab(button) {
  document.querySelectorAll('.panel').forEach(panel => { panel.hidden = panel.id !== button.dataset.tab; });
  document.querySelectorAll('[data-tab]').forEach(tab => { tab.setAttribute('aria-selected', String(tab === button)); tab.tabIndex = tab === button ? 0 : -1; });
}
/** Renders measured monthly project consumption independently of the last model response. Exigences : DEVAI-013, DEVAI-075. */
function quota(data, limit) { const used = Number(data?.consumed || 0), reserved = Number(data?.reserved || 0); if (Number.isFinite(limit) && limit > 0) { element('quota').max = limit; element('quota').value = Math.min(limit, used + reserved); element('quota-label').textContent = `${used.toLocaleString('fr-FR')} / ${limit.toLocaleString('fr-FR')}`; element('quota').title = `${reserved} tokens réservés`; } }
/** Shows normalized provider findings as text with explicit revision and pagination. Exigences : DEVAI-088, DEVAI-097. */
function report(m) {
  const source = m.source === 'SONAR' ? 'sonar' : 'xray', data = m.data;
  element(source).textContent = JSON.stringify(data, null, 2); element(`${source}-findings`).replaceChildren();
  element(`${source}-summary`).textContent = `${data.source || m.source} · ${data.freshness || data.status || 'Provenance à consulter'} · ${data.fingerprint?.slice(0, 12) || data.commit || ''}`;
  for (const finding of data.issues || []) { const article = document.createElement('div'); article.className = 'finding'; const title = document.createElement('strong'); title.textContent = finding.message || finding.summary || finding.key; const meta = document.createElement('p'); meta.className = 'hint'; meta.textContent = [finding.severity, finding.path, finding.line ? `Ligne ${finding.line}` : '', finding.component].filter(Boolean).join(' · '); article.append(title, meta); element(`${source}-findings`).append(article); }
  if (source === 'sonar') element('coverage-measured').textContent = Number.isFinite(data.coverage?.coverage) ? `Couverture Sonar mesurée : ${data.coverage.coverage}%` : 'Couverture non mesurée : configurer le rapport de couverture du projet.';
  if (source === 'sonar' && data.paging) { sonarPage = data.paging.pageIndex || 1; const pages = Math.max(1, Math.ceil(data.paging.total / (data.paging.pageSize || 50))); element('sonar-page').textContent = `Page ${sonarPage} / ${pages}`; element('sonar-previous').disabled = sonarPage <= 1; element('sonar-next').disabled = sonarPage >= pages; }
}
/** Renders verification separately from declarative model explanations, with accessible status labels. Exigences : DEVAI-090, DEVAI-098. */
function correctionReport(source, data) {
  const host = element(source === 'SONAR' ? 'sonar-correction' : 'xray-correction'); host.hidden = false; host.replaceChildren();
  const title = document.createElement('h3'); title.textContent = 'Rapport de correction';
  const summary = document.createElement('p'); summary.textContent = `${data.validated ? '✓ Compilation et tests validés' : '⚠ Validation incomplète'} · ${data.rounds} tentative(s)`;
  const message = document.createElement('p'); message.textContent = data.message;
  const branch = document.createElement('p'); branch.className = 'hint'; branch.textContent = `Branche : ${data.branch}`;
  const download = document.createElement('button'); download.textContent = '↓ Télécharger le PDF'; download.addEventListener('click', () => send({ type: 'downloadCorrectionReport', source }));
  host.append(title, summary, message, branch, download);
  if (data.newFindings?.length) { const warning = document.createElement('p'); warning.textContent = `⚠ ${data.newFindings.length} nouvelle(s) anomalie(s) : ${data.newFindings.map(item => item.message || item.key).join(' · ')}`; host.append(warning); }
  if (data.target !== undefined) { const coverage = document.createElement('p'); coverage.textContent = `Couverture : ${data.before}% → ${data.after ?? 'non mesurée'}% · Objectif : ${data.target}%`; host.append(coverage); }
  for (const item of data.items) {
    const card = document.createElement('article'); card.className = `correction-card ${item.status}`;
    const status = document.createElement('strong'); status.textContent = { corrected: '✓ Corrigé', partial: '◐ Corrigé partiellement', unimplemented: '○ Non implémenté' }[item.status];
    const heading = document.createElement('h4'); heading.textContent = `${/CRITICAL|BLOCKER|HIGH|High/.test(item.severity) ? '▲' : '◆'} ${item.severity} · ${item.title}`;
    const key = document.createElement('p'); key.className = 'hint'; key.textContent = `${item.key} · ${item.path}`;
    const evidence = document.createElement('p'); evidence.textContent = item.description;
    const explanation = document.createElement('p'); explanation.textContent = `Explication de l’assistant IA : ${item.explanation || 'Non fournie pour cette anomalie.'}`;
    const original = document.createElement('details'), label = document.createElement('summary'), raw = document.createElement('pre'); label.textContent = 'Constat du rapport initial'; raw.textContent = JSON.stringify(item.initial, null, 2); original.append(label, raw);
    card.append(status, heading, key, evidence, explanation, original); host.append(card);
  }
  const details = document.createElement('details'), label = document.createElement('summary'), log = document.createElement('pre'); label.textContent = 'Fichiers modifiés et validation'; log.textContent = `${data.files.join('\n')}\n\n${data.validationLog}`; details.append(label, log); host.append(details);
  selectTab(element(source === 'SONAR' ? 'tab-quality' : 'tab-security'));
}
element('increase-coverage').addEventListener('click', () => { const input = element('coverage-target'); if (input.reportValidity() && input.value.trim() && element('sonar-attempts').reportValidity()) send({ type: 'coverage', target: Number(input.value), attempts: Number(element('sonar-attempts').value) }); });
document.querySelectorAll('[data-tab]').forEach(button => { button.addEventListener('click', () => selectTab(button)); button.addEventListener('keydown', event => { const tabs = [...document.querySelectorAll('[data-tab]')]; let index = tabs.indexOf(button); if (event.key === 'ArrowRight') index = (index + 1) % tabs.length; else if (event.key === 'ArrowLeft') index = (index + tabs.length - 1) % tabs.length; else if (event.key === 'Home') index = 0; else if (event.key === 'End') index = tabs.length - 1; else return; event.preventDefault(); selectTab(tabs[index]); tabs[index].focus(); }); });
document.querySelectorAll('[data-suggestion]').forEach(button => button.addEventListener('click', () => { element('message').value = button.dataset.suggestion; element('message').focus(); composerState(); }));
document.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => send({ type: button.dataset.action })));
document.querySelectorAll('[data-git]').forEach(button => button.addEventListener('click', () => send({ type: 'git', action: button.dataset.git })));
document.querySelectorAll('[data-scan]').forEach(button => button.addEventListener('click', () => send({ type: 'scan', source: button.dataset.scan })));
document.querySelectorAll('[data-report]').forEach(button => button.addEventListener('click', () => send({ type: 'report', source: button.dataset.report })));
document.querySelectorAll('[data-remediate]').forEach(button => button.addEventListener('click', () => { const input = element(button.dataset.remediate === 'SONAR' ? 'sonar-attempts' : 'xray-attempts'); if (input.reportValidity() && input.value.trim()) send({ type: 'remediate', source: button.dataset.remediate, attempts: Number(input.value) }); }));
element('create').addEventListener('click', () => send({ type: 'starter', stack: element('stack').value, name: element('name').value }));
element('send').addEventListener('click', submit);
element('message').addEventListener('input', () => { composerState(); });
element('includeActive').addEventListener('change', contextStatus);
element('model-choice').addEventListener('change', () => { const alias = element('model-choice').value; if (alias) send({ type: 'selectModel', modelAlias: alias }); });
element('message').addEventListener('keydown', event => { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); submit(); } });
element('sonar-previous').addEventListener('click', () => send({ type: 'report', source: 'SONAR', page: sonarPage - 1 }));
element('sonar-next').addEventListener('click', () => send({ type: 'report', source: 'SONAR', page: sonarPage + 1 }));
window.addEventListener('message', event => {
  const m = event.data;
  switch (m.type) {
    case 'context': element('project-name').textContent = `Projet : ${m.name || 'Aucun'}`; element('context').textContent = `${m.branch || 'Sans branche'} · ${(m.stacks || []).join(' / ') || 'Stack à détecter'}`; element('context-chips').replaceChildren(); for (const stack of m.stacks || []) { const chip = document.createElement('span'); chip.className = 'chip'; chip.textContent = stack; element('context-chips').append(chip); } break;
    case 'capabilities': allowed = !!m.rule?.aiEnabled; modelCapabilities = m; renderModels(m); documentActions = new Set(m.rule?.allowedActions || []); element('capabilities').textContent = allowed ? 'IA autorisée' : 'IA indisponible'; element('capabilities').classList.toggle('neutral', !allowed); element('connection').textContent = m.projectId ? 'Connecté' : 'Non connecté'; element('connection').classList.toggle('neutral', !m.projectId); element('connection-detail').textContent = m.reason || `Politique ${m.policyVersion} · ${m.rule?.modelAlias || 'Aucun modèle'}`; element('model-identity').textContent = allowed ? modelLabel(m, 'Modèle non communiqué') : 'Modèle non disponible'; element('model-identity').title = allowed ? 'Modèle sélectionné par la politique du projet' : ''; element('model-identity').classList.toggle('neutral', !allowed || !m.provider || !m.model); contextStatus(); quota(m.usage, m.rule?.monthlyTokenQuota); composerState(); break;
    case 'busy': busy = m.value; composerState(); break;
    case 'remediation': element('mode').value = m.mode; element('task').value = m.task; selectTab(element('tab-ai')); composerState(); break;
    case 'accepted': if (element('message').value === m.text) element('message').value = ''; composerState(); break;
    case 'attachments': selectedPaths = m.paths; contextStatus(); element('attachments').replaceChildren(); for (const path of m.paths) { const chip = document.createElement('button'); chip.className = 'chip'; chip.textContent = `${path} ×`; chip.setAttribute('aria-label', `Retirer ${path}`); chip.addEventListener('click', () => send({ type: 'detach', path })); element('attachments').append(chip); } break;
    case 'quota': quota(m.usage, m.limit); break;
    case 'status': element('status').textContent = m.message; break;
    case 'error': element('status').textContent = m.message; entry('system', m.message); break;
    case 'info': entry('system', m.message); element('status').textContent = busy ? m.message : 'Opération terminée ; consulter le résultat'; break;
    case 'clearChat': element('conversation').replaceChildren(); break;
    case 'chat': element('streaming')?.remove(); entry(m.role, m.text, m); if (m.role === 'assistant' && allowed) { element('model-identity').textContent = modelLabel(m, 'Modèle non communiqué'); element('model-identity').title = 'Modèle utilisé pour la dernière réponse'; element('model-identity').classList.toggle('neutral', !m.provider || !m.model); } break;
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
    case 'provenance': element('provenance').textContent = `Recommandations ${m.origin} · empreinte ${String(m.hash).slice(0, 12)}`; break;
    case 'report': report(m); break;
    case 'correctionSettings': document.querySelectorAll('[data-attempts]').forEach(input => { input.value = String(m.maxAttempts); }); break;
    case 'correctionReport': correctionReport(m.source, m.data); break;
    case 'clearCorrections': for (const id of ['sonar-correction', 'xray-correction']) { element(id).replaceChildren(); element(id).hidden = true; } break;
    case 'git': element('git-output').textContent = JSON.stringify(m.data, null, 2); break;
  }
});
