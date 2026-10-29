# Installation du VSIX autonome 0.3.0

1. Installer VS Code Desktop 1.95.0 ou supérieur dans la série 1.x, Java 21, Git et Python 3.12. Vérifier `java -version`, `git --version` et, sous Windows, `py -3.12 --version`. Utiliser le VSIX construit pour le système et l’architecture du poste.
2. Installer le VSIX fourni par l’organisation via « Extensions: Install from VSIX » ou `suiteInstall/scripts/install-extension.bat`. La configuration des services est déjà compilée ; aucune URL ou clé n’est à saisir dans l’extension.
3. Ouvrir le dépôt et approuver le workspace. Le remote `origin` doit appartenir au GitLab du profil et le compte déjà configuré dans Git doit pouvoir le lire.
4. Ouvrir ODS. Le client vérifie réellement l’accès Git puis démarre automatiquement le gateway embarqué. Un refus Git bloque la connexion. Aucun SSO n’est demandé.
5. Consulter l’état du projet et de sa politique IA. Starter et GitFlow restent utilisables selon leurs prérequis ; les scans ne nécessitent pas le droit IA. Les corrections par LLM nécessitent ce droit.

À la fermeture de la fenêtre, désactivation de l’extension, déconnexion ou fermeture du projet, les processus possédés sont arrêtés. Masquer simplement la vue ne désactive pas l’extension. La perte du canal parent arrête également le gateway. Les données sont isolées par workspace ; l’ouverture simultanée du même workspace dans plusieurs fenêtres peut échouer sur le verrou de la base locale, sans arrêter la première instance.

## Quality et Security sans commit

Enregistrer les fichiers lorsque la boîte de dialogue le propose. Security → Générer SBOM lance le générateur de la stack choisie et propose l’export CycloneDX JSON. Télécharger SBOM réexporte le même document. Générer rapport Xray soumet le graphe au watch organisationnel. Le téléchargement est proposé hors du dépôt pour ne pas modifier son empreinte ; si un fichier est ajouté au dépôt entre deux étapes, régénérer le SBOM.

Quality → Analyser exécute le scanner sur les sources enregistrées et attend la tâche Sonar correspondante. Il s’agit d’une analyse à la demande, pas d’un diagnostic continu à chaque frappe comme SonarQube for IDE. Les issues sélectionnées peuvent être corrigées via le LLM, avec diff relu et validations. Un rapport devenu obsolète est refusé ; relancer l’analyse après modification.

| Stack | Génération SBOM | Analyse qualité |
|---|---|---|
| Java | Maven sur PATH, `pom.xml`, plugin CycloneDX 2.9.1 téléchargé au besoin | `mvn verify`, puis plugin Sonar Maven 5.0.0.4389 |
| Angular | Node/npm, `package-lock.json`, CLI CycloneDX npm 6.0.1 via npx | `sonar-scanner` sur PATH, configuration du projet |
| Python | `cyclonedx-py` sur PATH ; requirements épinglés, Poetry ou Pipenv lock | `sonar-scanner` sur PATH, configuration du projet |
| .NET | SDK .NET et outil `dotnet-CycloneDX` compatible avec `--spec-version` (5.5+), une solution/projet à la racine | `dotnet-sonarscanner`, begin/build/end ; paramètres privés via `SONARQUBE_SCANNER_PARAMS` |

Installer les versions de scanners qualifiées pour le serveur organisationnel. Le produit ne prétend pas que ces outils sont présents : une absence ou un échec empêche la création d’un rapport valide. Le compte Sonar doit pouvoir exécuter une analyse ; le compte Xray doit pouvoir lancer un Graph Scan sur le watch configuré. Les builds de projet exécutent du code et demandent une approbation native.

## Diagnostic et limites

« Accès Git refusé » : vérifier l’identité déjà configurée dans Git/IDE et la lecture du remote ; Actualiser renouvelle le contrôle. « Configuration requise » : faire corriger le profil par l’administrateur puis réinstaller le VSIX. Les exemples `*.organisation.example` sont inopérants.

Les identifiants embarqués restent extractibles malgré les champs verrouillés et logs masqués, conformément au choix accepté. Les instances métier et les licences doivent être qualifiées séparément ; les mocks de test ne prouvent pas leur disponibilité. Le démarrage manuel du JAR concerne uniquement le serveur séparé historique, pas l’installation normale du VSIX.
