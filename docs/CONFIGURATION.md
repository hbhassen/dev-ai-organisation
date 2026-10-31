# Configuration administrée au build — 0.3.1

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

Les valeurs `ODS_GATEWAY_PORT`, `ODS_BIND_ADDRESS`, `SPRING_PROFILES_ACTIVE` et `ODS_DATABASE_URL` du mode serveur séparé ne pilotent pas le lancement du VSIX : l’extension impose loopback, port dynamique, profil local et stockage H2 local par workspace. `ODS_LOCAL_TOKEN`, `ODS_WORKSPACE`, `ODS_REPOSITORY`, `ODS_EMBEDDED`, `ODS_RUNTIME_DIRECTORY` et `ODS_PYTHON_COMMAND` appartiennent au lancement natif et sont interdits dans un profil. Les anciennes variables OIDC et runner ne sont pas nécessaires au VSIX. PostgreSQL et le lancement manuel restent des capacités du serveur séparé historique.

## Vibe embarqué

Le packaging fige `mistral-vibe==3.0.3` et embarque son code, ses dépendances de plateforme et leurs licences. Java lance un adaptateur CLI Python isolé (`vibe_bridge.py`) qui utilise le backend Mistral officiel de Vibe et conserve les messages/outils typés ODS. Il ne lance pas l’interface interactive `vibe` et n’active pas ses outils de terminal, MCP ou hooks de projet. Cette séparation maintient les approbations, diff et quotas ODS. Les configurations personnelles `~/.vibe` ne sont pas importées. Python 3.12 est requis sur le poste cible ; le VSIX doit être construit pour la plateforme cible.

## Secrets, rotation et Git utilisateur

Les secrets embarqués sont extractibles par le propriétaire du poste, limite explicitement acceptée. L’interface verrouillée et le masquage des logs empêchent les modifications ordinaires et fuites accidentelles, pas l’extraction du VSIX. Distribuer les builds personnalisés dans un canal privé autorisé. La rotation demande un nouveau build et une réinstallation.

Les opérations Git gardent le credential helper/agent SSH du développeur. Pour une MR, l’extension cherche un token GitLab utilisateur dans le fournisseur d’authentification IDE ou Git Credential Manager. Un accès Git SSH ne crée pas un token API ; la MR explique l’indisponibilité si aucun credential API n’est déjà présent. Aucun écran de saisie de secret technique n’existe.

## Droits des documents GitFlow

Ajouter `CHANGELOG` et/ou `RELEASE_NOTES` dans `projects[].allowedActions` de la politique de gouvernance, puis la signer et la déployer selon le mécanisme existant. Ces actions sont refusées par défaut et ne sont pas déduites d'un droit `CHAT`, `CODE_GENERATION` ou `MR_DESCRIPTION`. Les tâches sont `git-changelog` et `git-release-notes`, exécutées en mode `MODIFICATION` avec contexte `LOCAL_GOVERNED`. Les couches de prompts signées peuvent compléter leurs instructions en conservant ces actions fixes. Aucun nouveau secret ni fournisseur IA n'est ajouté.
