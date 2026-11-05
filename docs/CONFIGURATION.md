# Configuration administrée au build — 0.7.0

Les corrections 0.3.1 ne changent pas le format du profil : conserver les URL et secrets administrés, reconstruire le VSIX puis recharger VS Code. Les erreurs de clé, quota et modèle sont maintenant distinguées d'une absence du runtime Vibe ; voir [Diagnostic](INSTALLATION.md#diagnostic-et-limites).

Le VSIX autonome ne demande aucune connexion SSO. L’extension vérifie le remote et exécute `git ls-remote` avec l’identité Git existante avant de démarrer le gateway. Un accès refusé ou un dossier non approuvé interdit la connexion. Le compte technique de gouvernance lit ensuite les métadonnées du dépôt vérifié et les politiques privées.

Les paramètres ci-dessous sont fournis par l’administrateur dans le profil de build, jamais saisis dans les onglets du développeur. Les exemples utilisent des placeholders. Pour injecter une valeur externe, écrire `${VARIABLE_BUILD}` comme valeur complète dans le fichier env du profil ; le packaging résout uniquement les références explicites et échoue si elles sont absentes. Aucun secret réel ne doit être commité.

| Variable | Utilisation exacte |
|---|---|
| `ODS_GITLAB_URL` | Origine HTTPS de GitLab ; le host doit correspondre au remote Git vérifié. |
| `ODS_GOVERNANCE_PROJECT_ID` | ID numérique du dépôt privé contenant la politique signée. |
| `ODS_GOVERNANCE_REF` | Référence protégée résolue en SHA immuable pour charger la politique. |
| `ODS_GOVERNANCE_TOKEN` | Token de service de lecture GitLab. Il lit aussi les métadonnées/fichiers des projets autorisés ; il ne publie jamais leurs commits ou MR. |
| `ODS_POLICY_PUBLIC_KEY` | Clé publique Ed25519 DER encodée Base64, utilisée pour vérifier les signatures de gouvernance. Ce n’est pas la clé privée de signature. |
| `ODS_LLM_TRANSPORT` | `VIBE_CLI` par défaut ; `API` sélectionne l’adaptateur HTTP Mistral direct. |
| `ODS_MISTRAL_URL` | Base HTTPS de l’API directe Mistral, sans suffixe `/v1/chat/completions`. |
| `ODS_MISTRAL_MODEL` | Identifiant réel du modèle approuvé ; la politique IA doit autoriser le même modèle et alias. |
| `ODS_MODEL_ALIAS` | Alias organisationnel contrôlé par la politique signée. |
| `ODS_MISTRAL_API_KEY` | Clé utilisée en mode API et en mode Vibe avec `ODS_VIBE_AUTH=API_KEY`. |
| `ODS_PROVIDER_REGISTRY` | JSON de fournisseurs génériques qualifiés ; seul le registre de déploiement autorise endpoints, hôtes, styles et références de credentials. |
| `ODS_OPENAI_API_KEY` | Clé API OpenAI Platform distincte de Mistral ; référencée dans le profil privé et jamais ajoutée en clair à la configuration ou au dépôt. |
| `ODS_VIBE_URL` | Base HTTPS du fournisseur Vibe, généralement `https://api.mistral.ai/v1`. Il ne s’agit pas d’un téléchargement de CLI. |
| `ODS_VIBE_AUTH` | `API_KEY` ou `CLI_TOKEN`. |
| `ODS_VIBE_TOKEN` | Credential Mistral accepté par le backend Vibe, fourni par l’organisation lorsque `CLI_TOKEN` est choisi. Un token de navigateur ou un cookie SSO arbitraire n’est pas assimilé à une clé Mistral. |
| `ODS_SONAR_URL` | Base HTTPS du serveur SonarQube utilisé par le scanner et la Web API. |
| `ODS_SONAR_USERNAME` | Nom administratif du compte de service associé au token ; information de configuration, sans substitution au token. Sonar s’authentifie avec son token, pas avec `username:token` comme mot de passe. |
| `ODS_SONAR_TOKEN` | Token du compte de service avec droit d’analyse et de lecture des résultats, pas un simple token de lecture. Transmis seulement au scanner Sonar et à sa Web API. |
| `ODS_JFROG_URL` | Base HTTPS de la plateforme JFrog ; les suffixes `/xray` ou `/xray/api` sont aussi reconnus par Graph Scan. |
| `ODS_JFROG_AUTH` | `BASIC` pour username/password ; `TOKEN` pour un Bearer token JFrog. |
| `ODS_JFROG_USERNAME` | Compte de service, obligatoire dans les deux modes. En mode TOKEN, l’identité est portée par le token ; le nom n’est pas ajouté artificiellement à l’en-tête Bearer. |
| `ODS_JFROG_PASSWORD` | Mot de passe de service utilisé uniquement en mode BASIC. |
| `ODS_JFROG_TOKEN` | Access token JFrog du compte de service utilisé uniquement en mode TOKEN. |
| `ODS_JFROG_WATCH` | Nom exact du watch appliqué au Graph Scan ; non modifiable dans Security. |
| `ODS_JFROG_PROJECT_KEY` | Clé projet JFrog facultative, transmise en paramètre `projectKey`. |
| `ODS_DATA_KEY_BASE64` | Clé AES de 32 octets encodée Base64 pour les données persistées. Si vide en mode embarqué, une clé stable est générée dans SecretStorage. Une perte de clé rend les anciennes sessions illisibles. |
| `ODS_PROMPT_DIRECTORY` | Répertoire facultatif des prompts organisationnels signés. Pour un VSIX autonome, préférer des fichiers livrés sous `runtime/config` et une configuration administrée adaptée ; aucun chemin propre au poste de build. |
| `ODS_PROMPT_PUBLIC_KEY` | Clé publique de vérification du bundle de prompts organisationnels. |

`application-organisation.properties`, déclaré dans `files`, contient les mappings `ods.sonar-project-<ID_GITLAB>=<CLE_SONAR>`. Il est copié dans `runtime/config` et chargé automatiquement. Le Graph Scan n’utilise plus `ods.jfrog-build-*` ni un numéro de build publié.

## Profil de build Mistral et OpenAI

Le profil `config/organisations/mistral-openai/organisation.json` est opt-in. Il active simultanément le transport Vibe Mistral et l’adaptateur OpenAI Chat Completions qualifié avec le runtime Vibe 3.0.3. Le profil par défaut `demo` reste Mistral uniquement.

Le bridge ODS conserve le backend générique Vibe et adapte son corps HTTP : température omise, plafond envoyé dans `max_completion_tokens`, redirections désactivées. Pour les familles `gpt-6-luna` et `gpt-6-sol`, il fixe `reasoning_effort=none`, compatible avec les appels d’outils Chat Completions. Cette qualification ne s’étend pas automatiquement aux autres modèles. Références : [contrat Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create) et [GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna).

Avant le build, fournir les références externes dans l'environnement du processus, sans les écrire dans les fichiers suivis par Git :

```powershell
$env:MISTRAL_API_KEY = '<CLE_MISTRAL>'
$env:OPENAI_API_KEY = '<CLE_API_OPENAI_PLATFORM>'
$env:MISTRAL_MODEL = '<MODELE_MISTRAL_APPROUVE>'
python tools/build.py --task Package --organisation-profile config/organisations/mistral-openai/organisation.json
```

Le build échoue si une des trois variables référencées est absente. Le package privé contient la configuration résolue ; la valeur OpenAI est associée uniquement à `ODS_OPENAI_API_KEY`. `ODS_PROVIDER_REGISTRY` autorise `https://api.openai.com/v1`, l'hôte `api.openai.com`, le style `openai` et les capacités qualifiées génération, outils et usage. Le modèle exact vient de `models[]` dans la politique signée ; elle doit contenir `allowedProviders: ["MISTRAL", "OPENAI"]`, des alias/modèles exacts, et les droits IA applicables. Une configuration build seule ne donne aucun droit à un projet. Exemple de catalogue pour la politique (les IDs doivent être confirmés par l'organisation) :

```json
{
  "allowedProviders": ["MISTRAL", "OPENAI"],
  "models": [
    {"alias": "mistral-approved", "provider": "MISTRAL", "model": "<MODELE_MISTRAL_APPROUVE>", "tools": true},
    {"alias": "openai-approved", "provider": "OPENAI", "model": "<MODELE_OPENAI_APPROUVE>", "tools": true, "maxContextTokens": 32768, "maxOutputTokens": 4096}
  ]
}
```

Avant usage réel, l'organisation doit approuver les données transmises, les règles de conservation et l'offre OpenAI Platform utilisée. Les limites `maxContextTokens` et `maxOutputTokens` doivent être adaptées à chaque modèle exact et rester sous ses limites fournisseur. ODS estime l'entrée depuis les octets UTF-8 sérialisés, réserve la sortie et refuse si le budget dépasse le contexte ; le compteur exact retourné par le fournisseur sert ensuite à la réconciliation. Un compte ou abonnement ChatGPT n'est pas une clé API Platform. Les appels réels ne sont pas effectués pendant les tests automatisés. Toute clé collée dans un ticket ou une conversation doit être révoquée et remplacée.

## JDK des composants ODS et du projet

Le JDK 21 est requis pour compiler et exécuter le moteur et la gateway ODS. Pour les commandes dans le workspace utilisateur, l'extension utilise les outils visibles sur le `PATH` et hérite de `JAVA_HOME`. `ods.javaPath` ne fixe que le runtime ODS. Si le projet déclare `java.version=25`, l'agent vérifie `mvn -version` et exécute le Maven installé sans injecter `-Djava.version=21` ni modifier le POM à cause de DEVAI-005.

Les valeurs `ODS_GATEWAY_PORT`, `ODS_BIND_ADDRESS`, `SPRING_PROFILES_ACTIVE` et `ODS_DATABASE_URL` du mode serveur séparé ne pilotent pas le lancement du VSIX : l’extension impose loopback, port dynamique, profil local et stockage H2 local par workspace. `ODS_LOCAL_TOKEN`, `ODS_WORKSPACE`, `ODS_REPOSITORY`, `ODS_EMBEDDED`, `ODS_RUNTIME_DIRECTORY` et `ODS_PYTHON_COMMAND` appartiennent au lancement natif et sont interdits dans un profil. Les anciennes variables OIDC et runner ne sont pas nécessaires au VSIX. PostgreSQL et le lancement manuel restent des capacités du serveur séparé historique.

## Vibe embarqué

Le packaging fige `mistral-vibe==3.0.3` et embarque son code, ses dépendances de plateforme et leurs licences. Java lance un adaptateur CLI Python isolé (`vibe_bridge.py`) qui utilise le backend Mistral officiel de Vibe et conserve les messages/outils typés ODS. Il ne lance pas l’interface interactive `vibe` et n’active pas ses outils de terminal, MCP ou hooks de projet. Cette séparation maintient les approbations, diff et quotas ODS. Les configurations personnelles `~/.vibe` ne sont pas importées. Python 3.12 est requis sur le poste cible ; le VSIX doit être construit pour la plateforme cible.

## Secrets, rotation et Git utilisateur

Les secrets embarqués sont extractibles par le propriétaire du poste, limite explicitement acceptée. L’interface verrouillée et le masquage des logs empêchent les modifications ordinaires et fuites accidentelles, pas l’extraction du VSIX. Distribuer les builds personnalisés dans un canal privé autorisé. La rotation demande un nouveau build et une réinstallation.

Les opérations Git gardent le credential helper/agent SSH du développeur. Pour une MR, l’extension cherche un token GitLab utilisateur dans le fournisseur d’authentification IDE ou Git Credential Manager. Un accès Git SSH ne crée pas un token API ; la MR explique l’indisponibilité si aucun credential API n’est déjà présent. Aucun écran de saisie de secret technique n’existe.

## Corrections et couverture automatiques (0.5.0)

Dans Quality ou Security, lancer une analyse puis utiliser **Corriger les issues**. Le dossier doit avoir un état Git propre : enregistrer les modifications existantes dans Git avant de démarrer. L’extension crée une branche `fix/ods-*`, applique les propositions du modèle avec checkpoints, exécute compilation et tests puis régénère le rapport Sonar ou le SBOM et le rapport Xray. Le diff s’ouvre seulement après validation de la compilation et des tests. Une anomalie est déclarée corrigée uniquement si le nouveau rapport confirme sa disparition.

Le champ **Nombre maximal de tentatives** accepte un entier de 1 à 100, avec 3 comme valeur initiale. La valeur choisie est mémorisée dans le workspace. Chaque tentative peut comporter plusieurs échanges du modèle et consommer le quota ; **Arrêter** interrompt le parcours. Une limite atteinte, une erreur fournisseur ou une annulation conserve la branche et produit un bilan incomplet, sans succès inventé.

Dans Quality, **Augmenter la couverture** utilise le pourcentage demandé et la même limite de tentatives. Le projet doit déjà produire un rapport de couverture importé par Sonar (par exemple JaCoCo XML pour Maven). Les métriques absentes ne sont pas assimilées à zéro. L’extension contrôle que les fichiers et lignes initialement mesurés restent présents et refuse les modifications de production dans ce parcours. Les propositions qui ajoutent des exclusions ou désactivent des tests sont refusées ; ces contrôles ne remplacent pas la revue humaine des tests produits.

Les contrôles exécutés sont Maven `clean verify` avec tests activés, scripts npm `build` puis `test -- --watch=false`, Python `compileall` puis `pytest`, ou .NET `build` puis `test`. Les prérequis doivent être installés sur le poste. Pour Maven, un rapport Surefire/Failsafe avec au moins un test exécuté est obligatoire.

Le rapport de correction apparaît dans l’onglet d’origine avec un état par constat initial, la criticité et les explications du modèle distinguées des preuves de validation. **Télécharger le PDF** exporte ce rapport via une boîte de dialogue native. Les rapports sont conservés en mémoire pour le dossier actif ; les télécharger avant de recharger VS Code. Aucun commit, push ou MR n’est créé par ces boutons.

## Droits des documents GitFlow

Ajouter `CHANGELOG` et/ou `RELEASE_NOTES` dans `projects[].allowedActions` de la politique de gouvernance, puis la signer et la déployer selon le mécanisme existant. Ces actions sont refusées par défaut et ne sont pas déduites d'un droit `CHAT`, `CODE_GENERATION` ou `MR_DESCRIPTION`. Les tâches sont `git-changelog` et `git-release-notes`, exécutées en mode `MODIFICATION` avec contexte `LOCAL_GOVERNED`. Les couches de prompts signées peuvent compléter leurs instructions en conservant ces actions fixes. Aucun nouveau secret ni fournisseur IA n'est ajouté.


### Mapping Sonar dynamique (0.6.0, DEVAI-188)

Dans une règle existante de `projects` du fichier `ai-project-policy.json`, ajouter `"sonarProjectKey": "backend-client"` à côté de `projectId`. La clé signée est prioritaire sur `ods.sonar-project-<ID_GITLAB>` dans `application-organisation.properties`. Le fichier properties reste un repli lorsque le champ ou la règle est absent. Un mapping manquant dans les deux sources bloque l’analyse. Une politique invalide ou indisponible ne permet pas de contourner la vérification par le repli.

La clé Sonar accepte 1 à 400 caractères parmi lettres ASCII, chiffres, `-`, `_`, `.`, `:` et au moins un caractère non numérique. L’analyse reste possible pour une règle `aiEnabled: false` ; les autorisations IA ne sont pas élargies.

Pour ajouter un projet : créer le projet Sonar et accorder les droits nécessaires au compte de service ; ajouter sa règle et sa clé Sonar dans la politique ; augmenter `policyVersion`, renouveler les dates (24 heures au maximum), signer les octets finaux et publier JSON et SIG ensemble. Attendre au maximum 60 secondes pour le cache puis actualiser, ou reconnecter l’extension. Aucun changement de clé de confiance ni rebuild n’est requis. Un premier VSIX 0.6.0 doit cependant être installé : les versions antérieures refusent ce champ inconnu. Les URL et credentials des services restent administrés au build.
