# Installation du VSIX autonome 0.5.0

Dans GitFlow, pousser la branche source puis utiliser **Créer une MR**. Choisir la cible, le titre et les commentaires facultatifs ; la pré-revue Mistral se génère automatiquement. Lire l’aperçu Markdown complet puis confirmer **Publier**. La description conserve les commentaires et contient, pour chaque fichier, une recommandation au reviewer, une éventuelle question au développeur et un test suggéré. La pré-revue ne vaut pas approbation et n’exécute aucun test.

La politique doit autoriser `MR_DESCRIPTION`. **Préparer la description IA** génère le même aperçu sans publier ; sa réutilisation évite un appel fournisseur si les branches sont inchangées. Seuls les commits poussés sont analysés. Un diff GitLab tronqué, sensible, supérieur à 30 fichiers ou à 12 000 caractères bloque la génération : découper alors la MR. Un fichier binaire ou un changement de métadonnées sans diff textuel exige une vérification humaine. Une réponse IA incomplète ou un changement de branche bloque la publication. Si une MR existe déjà, elle est signalée et conservée sans modification ; le nouveau texte reste dans l’aperçu.

1. Installer VS Code Desktop 1.95.0 ou supérieur dans la série 1.x, Java 21, Git et Python 3.12. Vérifier `java -version`, `git --version` et, sous Windows, `py -3.12 --version`. Utiliser le VSIX construit pour le système et l’architecture du poste.
2. Installer le VSIX fourni par l’organisation via « Extensions: Install from VSIX » ou `suiteInstall/scripts/install-extension.bat`. La configuration des services est déjà compilée ; aucune URL ou clé n’est à saisir dans l’extension.
3. Ouvrir le dépôt et approuver le workspace. Le remote `origin` doit appartenir au GitLab du profil et le compte déjà configuré dans Git doit pouvoir le lire.
4. Ouvrir ODS. Le client vérifie réellement l’accès Git puis démarre automatiquement le gateway embarqué. Un refus Git bloque la connexion. Aucun SSO n’est demandé.
5. Consulter l’état du projet et de sa politique IA. Starter et GitFlow restent utilisables selon leurs prérequis ; les scans ne nécessitent pas le droit IA. Les corrections par LLM nécessitent ce droit.

À la fermeture de la fenêtre, désactivation de l’extension, déconnexion ou fermeture du projet, les processus possédés sont arrêtés. Masquer simplement la vue ne désactive pas l’extension. La perte du canal parent arrête également le gateway. Les données sont isolées par workspace ; l’ouverture simultanée du même workspace dans plusieurs fenêtres peut échouer sur le verrou de la base locale, sans arrêter la première instance.

## Quality et Security sans commit

Enregistrer les fichiers lorsque la boîte de dialogue le propose. Security → Générer SBOM lance le générateur de la stack choisie et propose l’export CycloneDX JSON. Télécharger SBOM réexporte le même document. Générer rapport Xray soumet le graphe au watch organisationnel. Le téléchargement est proposé hors du dépôt pour ne pas modifier son empreinte ; si un fichier est ajouté au dépôt entre deux étapes, régénérer le SBOM.

Quality → Analyser exécute le scanner sur les sources enregistrées et attend la tâche Sonar correspondante. Il s’agit d’une analyse à la demande, pas d’un diagnostic continu à chaque frappe comme SonarQube for IDE. Un rapport devenu obsolète est refusé ; relancer l’analyse après modification.

Dans Quality et Security, **Corriger les issues** propose les issues du dernier rapport. Valider cette sélection autorise Mistral à lire les sources utiles et à appliquer les correctifs de cette tâche. Le mode Agent est sélectionné automatiquement : aucune consigne complémentaire n’est nécessaire. Les fichiers sont sauvegardés dans un checkpoint récupérable, modifiés, puis le diff avant/après s’ouvre. Les buffers non enregistrés et les empreintes obsolètes bloquent l’écriture. Les commandes de build/tests conservent leurs autorisations natives. Relancer ensuite Sonar ou générer un nouveau SBOM et rapport Xray pour vérifier les issues ; appliquer un patch ne prouve pas leur résolution. Une réponse sans patch déclenche au maximum deux relances automatiques ; si aucun changement n’est appliqué, un message l’indique. Le bouton Arrêter interrompt le parcours ; les modifications déjà appliquées restent restaurables via le checkpoint. Les autres tâches IA conservent la validation du diff avant application.

Pour les projets Maven sans wrapper, l’agent peut utiliser `mvn` installé sur le `PATH`. Les projets qui fournissent leur wrapper peuvent continuer à utiliser `mvnw`. Ces commandes demandent l’autorisation native d’exécution ; aucun shell libre n’est ajouté à la liste des outils autorisés.

| Stack | Génération SBOM | Analyse qualité |
|---|---|---|
| Java | Maven sur PATH, `pom.xml`, plugin CycloneDX 2.9.1 téléchargé au besoin | `mvn verify`, puis plugin Sonar Maven 5.0.0.4389 |
| Angular | Node/npm, `package-lock.json`, CLI CycloneDX npm 6.0.1 via npx | `sonar-scanner` sur PATH, configuration du projet |
| Python | `cyclonedx-py` sur PATH ; requirements épinglés, Poetry ou Pipenv lock | `sonar-scanner` sur PATH, configuration du projet |
| .NET | SDK .NET et outil `dotnet-CycloneDX` compatible avec `--spec-version` (5.5+), une solution/projet à la racine | `dotnet-sonarscanner`, begin/build/end ; paramètres privés via `SONARQUBE_SCANNER_PARAMS` |

Installer les versions de scanners qualifiées pour le serveur organisationnel. Le produit ne prétend pas que ces outils sont présents : une absence ou un échec empêche la création d’un rapport valide. Le compte Sonar doit pouvoir exécuter une analyse ; le compte Xray doit pouvoir lancer un Graph Scan sur le watch configuré. Les builds de projet exécutent du code et demandent une approbation native.

## Diagnostic et limites

Depuis 0.3.1, le bridge Vibe restaure son répertoire courant avant de supprimer son dossier temporaire sous Windows. La version 0.3.0 pouvait perdre une réponse réussie pendant ce nettoyage et afficher à tort « Vibe indisponible ». Réinstaller le VSIX 0.3.1 et recharger la fenêtre pour appliquer la correction.

`MODEL_AUTHENTICATION_FAILED` indique un refus de la clé administrée ; `MODEL_RATE_LIMITED`, une limite de débit ou de quota ; `MODEL_UNAVAILABLE`, un modèle ou endpoint indisponible. `DEPENDENCY_UNAVAILABLE` reste utilisé pour un runtime manquant ou une autre panne du bridge. Le détail privé des réponses fournisseur n'est jamais affiché.

« Accès Git refusé » : vérifier l’identité déjà configurée dans Git/IDE et la lecture du remote ; Actualiser renouvelle le contrôle. « Configuration requise » : faire corriger le profil par l’administrateur puis réinstaller le VSIX. Les exemples `*.organisation.example` sont inopérants.

Les identifiants embarqués restent extractibles malgré les champs verrouillés et logs masqués, conformément au choix accepté. Les instances métier et les licences doivent être qualifiées séparément ; les mocks de test ne prouvent pas leur disponibilité. Le démarrage manuel du JAR concerne uniquement le serveur séparé historique, pas l’installation normale du VSIX.

## Changelog et notes de release dans GitFlow

Le bouton **Changelog** crée `CHANGELOG.md` à la racine s'il est absent, ou demande au LLM de mettre à jour son contenu en préservant l'historique. **Release notes** suit le même parcours pour `RELEASE_NOTES.md`. La version sans publication confirmée reste un brouillon « Non publié ».

La politique signée doit autoriser respectivement `CHANGELOG` et `RELEASE_NOTES`, avec `aiEnabled: true` et `contextMode: LOCAL_GOVERNED`. Ces droits sont distincts ; un ancien profil ne les accorde pas automatiquement. Le mode Modification est sélectionné par le bouton. Les URL et identifiants LLM restent ceux du VSIX administré.

Enregistrer les fichiers modifiés lorsque l'extension le propose, puis autoriser la lecture Git et le contexte selon les dialogues natifs. La tâche utilise l'état local et les 30 derniers commits au maximum ; ce périmètre ne représente pas automatiquement toutes les modifications depuis le dernier tag. Le LLM peut inspecter les fichiers utiles. Relire le diff proposé avant d'accepter : une empreinte devenue obsolète bloque l'application. Les notes anciennes doivent être conservées et les doublons évités.

Une génération ne crée aucun commit, push, tag ou publication GitHub/GitLab. Une panne LLM, un refus du diff ou une réponse sans proposition laisse le document inchangé ; consulter alors la conversation IA. Seul le document sélectionné peut être créé ou modifié par cette tâche, jamais supprimé. Les autres noms et emplacements de changelog ne sont pas détectés automatiquement.
