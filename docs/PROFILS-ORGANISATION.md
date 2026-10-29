# Compiler pour une organisation

Le point d’entrée `script.bat` reconstruit tout le produit : Maven, tests des connecteurs avec services simulés, TypeScript, tests de la webview, VSIX et package autonome. Il accepte un fichier JSON conforme à `config/organisation.schema.json`. Les validations supplémentaires de chemins, d’URL et de fichiers sont exécutées par `tools/organisation_profile.py` avant la compilation.

L'orchestration utilise Python 3.11+ (`tools/build.py`) sans appel PowerShell, y compris pour obtenir Maven. Les mêmes prérequis JDK 21, Node 24/npm et Git restent nécessaires. Le lanceur de gateway livré utilise également Python 3.11+.

```bat
script.bat config\organisations\demo\organisation.json
```

Ou définir `ODS_ORGANISATION_PROFILE` puis exécuter `script.bat`. Un argument explicite a priorité. Les chemins contenus dans le profil sont relatifs au dossier du profil ; utiliser `/` dans le JSON. Une URL distante de gateway doit utiliser HTTPS. Aucune commande de shell n’est évaluée depuis les fichiers de configuration.

```json
{
  "schemaVersion": 1,
  "id": "organisation-exemple",
  "extension": {
    "name": "outils-developpement",
    "publisher": "organisation-exemple",
    "displayName": "Mon organisation — Développement"
  },
  "gatewayUrl": "https://gateway.organisation.example",
  "envFiles": ["gateway.env"],
  "environment": {
    "ODS_MISTRAL_MODEL": "<MODELE_MISTRAL_APPROUVE>",
    "ODS_MISTRAL_API_KEY": "${ORG_MISTRAL_KEY}",
    "ODS_PROMPT_DIRECTORY": "config/prompts"
  },
  "files": [
    {"source": "application-organisation.properties", "target": "application-organisation.properties"},
    {"source": "prompts/bundle.json", "target": "prompts/bundle.json"},
    {"source": "prompts/bundle.sig", "target": "prompts/bundle.sig"},
    {"source": "prompts/global.md", "target": "prompts/global.md"}
  ]
}
```

Tous les fichiers du bundle signé doivent être déclarés, y compris les profils Java/Angular/Python/.NET et les tâches référencées. Les valeurs `environment` surchargent les fichiers `envFiles`, lus dans l’ordre. Seules les variables `ODS_*` et `SPRING_PROFILES_ACTIVE` sont acceptées. Au démarrage, une valeur entière `${NOM_VARIABLE}` est résolue dans l’environnement du processus ; une variable absente bloque le démarrage. Les valeurs ne sont pas affichées dans les logs de build.

Le VSIX contient uniquement les éléments publics : identité, nom affiché, libellés et URL de gateway. Le callback OIDC suit l’identité réellement installée : `vscode://<publisher>.<name>/callback`. Enregistrer ce callback dans le SSO. Installer une seule variante ODS par profil VS Code : les identifiants de commandes et vues restent `ods.*` pour conserver la compatibilité.

La gateway reçoit `suiteInstall/config/gateway.env` et les fichiers déclarés sous `suiteInstall/config/`. Le script de démarrage charge aussi `config/application-organisation.properties`, destiné notamment aux mappings `ods.sonar-project-<ID>` et `ods.jfrog-build-<ID>`. Les starters organisationnels conservent leur configuration côté processus VS Code ; voir [CONFIGURATION.md](CONFIGURATION.md). Un bundle de prompts ou un catalogue de starters doit toujours satisfaire les contrôles de signature existants.

Le staging du VSIX est créé dans un nouveau dossier ignoré à chaque compilation. Les manifestes source restent inchangés. `BUILD.json` indique le commit, le profil et l’état des sources ; `config/organisation.json` identifie les paramètres publics et l’empreinte du profil. `config/CONFIG-SHA256SUMS` contient aussi les empreintes des configurations effectivement livrées, y compris les valeurs provenant de fichiers externes au dépôt. L’archive finale garde la version applicative Maven/VSIX ; le nom du VSIX suit `extension.name`. Copier la livraison avant de compiler un autre profil ; le build préserve aussi automatiquement le précédent `suiteInstall` sous `.artifacts`.

## Configuration livrée et reprise

Le profil `demo` comporte des URL d’exemple et des paramètres à remplacer. Les mocks vivent exclusivement dans les tests ; ce profil n’active aucun faux service dans l’application. Aucune clé réelle d’organisation n’a été fournie pour cette livraison. Les clés privées de test OIDC/TLS/signature sont créées en mémoire ou dans un fichier temporaire supprimé après chargement. Les valeurs synthétiques des fixtures ne donnent accès à aucun service.

À la demande explicite de l’utilisateur, la branche `dev/toreprise` conserve exceptionnellement un instantané de `suiteInstall`, configuration d’exemple comprise. Cette exception ne change pas l’exclusion des artefacts sur `main` et `develop`. Le package provient du commit tagué ; le commit d’instantané est un commit ultérieur et ne remplace pas `BUILD.json.sourceCommit`. Ne pas fusionner les binaires de cette branche vers les branches source. Les futurs secrets réels ne sont pas inventés ni récupérés dans les credentials du poste.

La branche `script/vsix` constitue une seconde exception explicitement demandée pour le correctif 0.2.1 : le code y est commité avant la reconstruction, puis le dossier `suiteInstall/` correspondant est ajouté dans un commit distinct. `BUILD.json.sourceCommit` désigne ce commit source, pas le commit ultérieur contenant les binaires. Cet instantané de branche n'implique pas la création d'un tag ni d'une release GitHub.

## Références de mise en œuvre

Les noms installés, vues et paramètres utilisent les [points de contribution VS Code](https://code.visualstudio.com/api/references/contribution-points). Les contrôles navigateur utilisent [Playwright Test](https://playwright.dev/docs/test-configuration). Le connecteur Xray lit le [Build Summary v2 officiel](https://docs.jfrog.com/security/reference/build-summary_artifacts-v2-openapi) ; les fixtures isolées ne qualifient pas à elles seules la version déployée dans une organisation.
