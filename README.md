# Organisation Development Standard — 0.2.1

Extension VS Code à cinq vues avec moteur Java 21/Maven et gateway Spring Boot. Le code implémente les starters standards, les outils locaux, les diffs avec approbation, un agent Mistral, les politiques signées, les quotas, les connecteurs GitLab/Sonar/Xray et les prompts organisationnels remplaçables.

Le périmètre cible reste celui des 166 exigences de [specification.md](specification.md). L'état détaillé et les limites de qualification figurent dans [docs/CONFORMITE.md](docs/CONFORMITE.md). Une compilation réussie ne constitue pas une qualification des services de l'organisation.

## Construire sous Windows

Préparer **JDK 21 sur PATH**, **Node.js 24 avec npm**, **Python 3.11+** et Git. Les scripts `.bat` et `mvnw.cmd` ne lancent pas PowerShell. `tools/build.py` orchestre la compilation et les tests ; `tools/maven_wrapper.py` télécharge Maven 3.9.11 selon `.mvn/wrapper/maven-wrapper.properties`, vérifie son SHA-256 et le conserve dans le cache ignoré `.artifacts/maven/`. Les versions des dépendances sont verrouillées par Maven et `package-lock.json`.

```bat
build.bat
generate-vsix.bat
package.bat
script.bat config\organisations\demo\organisation.json
```

- `build.bat` : contrôle des commentaires/IDs, build Maven avec tests, `npm ci`, compilation/lint/tests TypeScript et démarrage réel des JAR.
- `generate-vsix.bat` : même validation, puis création et inspection de `.artifacts/organisation-development-standard-0.2.1.vsix`, moteur inclus.
- `package.bat` : même validation, puis dossier autonome `suiteInstall/` et archive `suiteInstall-0.2.1.zip`, avec tests depuis les JAR livrés.

La compilation par organisation est décrite dans [PROFILS-ORGANISATION](docs/PROFILS-ORGANISATION.md). Elle surcharge notamment le nom du plugin après installation, son identité et sa gateway. Les tests navigateur téléchargent Chromium via la version verrouillée de Playwright.

Chaque script renvoie un code non nul en cas d'échec. Aucun `skipTests`. Les scripts ne publient pas automatiquement les livrables et ne modifient pas votre installation VS Code pendant le build.

`scripts/start-gateway.bat` appelle `scripts/start_gateway.py` et demande aussi Python 3.11+ sur la machine de livraison. Il charge `gateway.env` sans évaluer son contenu comme des commandes, résout les références `${VARIABLE}` et démarre Java directement. `scripts/install-extension.bat` utilise la CLI `code`. Les points d'entrée `.ps1` conservés pour compatibilité restent facultatifs et ne sont appelés par aucun script batch du projet.

## Installer et configurer

Voir [INSTALLATION](docs/INSTALLATION.md) et [CONFIGURATION](docs/CONFIGURATION.md). La gateway démarre localement avec H2 et refuse les API métier tant que SSO et services ne sont pas configurés. PostgreSQL est prévu pour la production. Les URL `*.organisation.example` sont des exemples à remplacer ; aucune connexion réussie n'est simulée.

Le fournisseur initial est exclusivement **Mistral**. `ModelProvider` est le point d'extension futur ; ajouter un autre fournisseur nécessite une évolution de gouvernance, des tests et un déploiement serveur.

## Organisation du code

| Répertoire | Responsabilité |
|---|---|
| `vscode-extension` | Vues, OIDC/SecretStorage, confiance IDE, diff, diagnostics, permissions |
| `ods-contracts` | JSON borné et erreurs métier |
| `ods-java-core` | IPC, outils, processus, checkpoints, starters, signatures, prompts |
| `ods-gateway` | Autorisation, Mistral, quotas/jobs/audit, GitLab, Sonar et Xray |
| `prompt-content` | Vrais fichiers génériques globaux/par stack et prompts de tâches |
| `scripts`, `tools` | Build, contrôles, packaging et gouvernance hors ligne |
| `examples`, `config`, `docs` | Publication des règles, déploiement et exploitation |

Les commentaires des symboles portent leurs références `DEVAI-xxx`. Le contrôle syntaxique ne remplace pas une revue de pertinence. Les binaires et packages restent exclus des branches source. Exceptions explicitement demandées : l'ancien instantané sur `dev/toreprise` et la livraison `suiteInstall/` du correctif 0.2.1 sur `script/vsix`. Sur cette dernière branche, le commit de livraison suit le commit source identifié dans `suiteInstall/BUILD.json` ; les binaires ne doivent pas être fusionnés dans `main` ou `develop`. Aucun secret réel d’organisation n’a été fourni.
