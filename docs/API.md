# API du VSIX autonome 0.3.0

Le gateway local écoute sur loopback, port attribué au lancement. Chaque route métier exige `X-ODS-Local-Token`, jeton aléatoire privé créé par le client natif après le contrôle Git. Aucun SSO n’est requis. Les routes de projet sont limitées au dépôt validé au lancement. `X-ODS-GitLab-Token` est utilisé uniquement pour la MR avec le credential utilisateur existant. Les POST exigent `Idempotency-Key`.

| Route | Corps / résultat |
|---|---|
| POST `/api/v1/security/sbom` | `projectId`, `profile` (JAVA, ANGULAR, PYTHON, DOTNET) ; job LOCAL_SBOM dont le résultat contient `sbom`, `fingerprint`, `profile`. |
| POST `/api/v1/security/scans` | `projectId`, `profile`, `sbomJobId` ; vérifie le propriétaire, la stack et l’empreinte du SBOM avant Graph Scan. |
| POST `/api/v1/quality/scans` | `projectId`, `profile` ; exécution locale et résultat lié à `ceTaskId`, `analysisId`, `fingerprint`, issues et gate. |
| GET `/api/v1/workspace/fingerprint?projectId=...` | Empreinte des sources enregistrées ; utilisée avant remédiation. |
| GET `/api/v1/jobs/{id}` | Résultat appartenant à la session locale ; aucun credential dans le DTO. |
| POST `/api/v1/jobs/{id}/cancel` | Annulation de la tâche possédée. Un appel déjà soumis au fournisseur peut continuer côté service. |

Les anciens endpoints GET de rapports de build ne sont pas utilisés par l’interface autonome. Le contrat IA, les outils typés, les prompts et les MR ci-dessous restent disponibles. Les détails OIDC et runners qui suivent concernent uniquement le serveur séparé historique.

---

# Contrats exécutables — version 1

Références : DEVAI-014, DEVAI-117, DEVAI-118, DEVAI-119, DEVAI-151 à DEVAI-166. La source normative du comportement livré est `GatewayController`, `EngineMain`, `ToolCatalog` et leurs validations.

## REST

Dans le serveur séparé historique, les routes métier nécessitent un bearer OIDC valide pour l’audience configurée. Les routes liées à GitLab demandent également `X-ODS-GitLab-Token` ; la gateway vérifie son propriétaire. Les créations de tâche et nouveaux tours demandent `Idempotency-Key` (8 à 128 caractères alphanumériques, `_` ou `-`). Une clé ne doit pas être réutilisée avec un autre corps.

| Méthode et chemin | Fonction |
|---|---|
| GET `/health/live`, `/health/ready` | Santé publique ; readiness indique la configuration, pas une qualification des tiers |
| GET `/api/v1/auth/config` | Métadonnées publiques du client OIDC |
| GET `/api/v1/projects/resolve?path=group/project` | Identité canonique GitLab et accès utilisateur |
| GET `/api/v1/capabilities?projectId=1042` | Droits et quotas effectifs du projet |
| GET `/api/v1/prompts` | Prompts génériques et organisationnels approuvés |
| GET `/api/v1/prompts/effective?stack=JAVA&task=code-generation` | Composition et provenance des recommandations |
| POST `/api/v1/ai/chat` | Question ou Modification |
| POST `/api/v1/ai/agents/run` | Agent ; nécessite aussi `AGENT_RUN` et l'agent autorisé |
| POST `/api/v1/ai/agents/{id}/tool-results` | Résultat d'un appel attendu, accepté une seule fois |
| POST `/api/v1/ai/agents/{id}/turns` | Nouveau tour ou reprise explicite |
| GET `/api/v1/jobs/{id}` | État persistant réservé au propriétaire |
| GET `/api/v1/jobs/{id}/events` | SSE de présentation ; le job reste la source d'état final |
| POST `/api/v1/jobs/{id}/cancel` | Demande d'annulation sans annuler la consommation acquise |
| DELETE `/api/v1/ai/sessions/{id}` | Effacement du contenu conversationnel par son propriétaire |
| GET `/api/v1/ai/usage?projectId=1042` | Consommation/réservations projet et utilisateur, mois UTC |
| POST `/api/v1/quality/scans`, `/api/v1/security/scans` | Pipeline réel sur commit immuable et profil autorisé |
| GET `/api/v1/quality/reports?projectId=1042&page=1` | Issues normalisées, Quality Gate et révision Sonar |
| GET `/api/v1/security/reports?projectId=1042&buildNumber=42` | Rapport réel du build Xray mappé par l'administration |
| POST `/api/v1/git/merge-requests` | Création de MR avec l'identité développeur ; recherche préalable de doublon |

Exemple de demande (IDs et contenu illustratifs) :

```json
{"projectId":1042,"mode":"AGENT","task":"implement-requirement","message":"Implémenter l'exigence du projet avec tests et documentation","stacks":["JAVA"],"context":[]}
```

Exemple de résultat d'outil :

```json
{"toolCallId":"<EXPECTED_CALL_ID>","result":{"exitCode":0,"output":"<REAL_FILTERED_OUTPUT>","running":false}}
```

Le client ne peut ajouter ni `system`, ni URL fournisseur, ni clé technique, ni outil exécutable au catalogue. Le mode `SERVER_VERIFIED` récupère les blobs par commit via GitLab et réduit fortement les outils autorisés ; il n'atteste pas des modifications non publiées.

## IPC local

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
```

Après `workspace.open` avec racine et confiance issues de l'API native VS Code : `project.inspect`, `workspace.list`, `files.read`, `search.text`, `project.instructions`, `changes.propose`, `changes.apply`, `checkpoints.restore`, `process.start`, `process.output`, `process.cancel`. Les alias `build.run` et `tests.run` utilisent le même exécuteur contrôlé.

`starters.list`, `starters.create`, `git.clone` et `prompts.preview` gèrent l'initialisation. Les opérations qui écrivent ou exécutent exigent `approved: true`, fourni après confirmation native, jamais accepté directement depuis la webview.

Un changement contient `path`, `baseHash` (`MISSING` pour création), `content` (null pour suppression) et `requirements`. La proposition retourne `planHash`. L'application relit chaque base et vérifie le hash du plan approuvé. La restauration utilise `checkpointId` et refuse toute modification ultérieure des fichiers concernés.

## Erreurs et limites

Les erreurs portent un code stable et un message filtré : `ACTION_DENIED`, `MODEL_NOT_ALLOWED`, `POLICY_INVALID`, `POLICY_EXPIRED`, `POLICY_REPLAYED`, `QUOTA_EXCEEDED`, `RATE_LIMITED`, `STALE_BASE`, `STALE_REPORT`, `CONTEXT_REJECTED`, `DEPENDENCY_UNAVAILABLE`, `IDEMPOTENCY_CONFLICT`. Les schémas rejettent les champs inattendus sur les demandes sensibles. Les limites de contexte, processus, durée et concurrence sont documentées dans le code et `CONFIGURATION.md`.

## Présentation des rapports en 0.2.0

`GET /api/v1/quality/reports` accepte `page` entre 1 et 10000 et retourne `paging` avec les résultats normalisés. La vue utilise Précédent/Suivant. `GET /api/v1/security/reports` expose `issues` normalisées (`key`, `message`, `severity`, `components`, `cves`, `providerEvidence`) et conserve `report`. `commitVerified: false` indique explicitement que le build summary ne prouve pas sa correspondance avec le commit du workspace.

Les boutons de documents GitFlow utilisent le contrat existant `POST /api/v1/ai/chat` avec `mode: MODIFICATION` et `task: git-changelog` ou `git-release-notes`. Les actions de politique correspondantes sont `CHANGELOG` et `RELEASE_NOTES`. Le LLM propose via `changes_propose` ; le client limite la cible à `CHANGELOG.md` ou `RELEASE_NOTES.md` et applique le diff uniquement après validation. Il n'existe pas de nouvel endpoint de publication de release.
