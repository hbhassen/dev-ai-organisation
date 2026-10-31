# Validation du banc et des corrections ODS 0.3.1 — 31 octobre 2026

Les deux défauts reproduits ont été corrigés à la demande de l'utilisateur. La version 0.3.1 est installée dans le profil VS Code `Test`. Les validations ci-dessous ont réellement été exécutées sur Windows. La validation initiale comprenait un seul appel Mistral réel ; le parcours Quality ajouté en fin de document comprend ensuite une correction générée par Mistral.

## Corrections vérifiées

- **Bridge Vibe / Windows** : le processus supprimait son dossier temporaire alors que celui-ci était encore son répertoire courant. Une réponse réussie devenait une erreur `WinError 32`, présentée comme `DEPENDENCY_UNAVAILABLE`. Le bridge restaure maintenant son répertoire initial dans un `finally`, avant le nettoyage. Le test exécute le vrai point d'entrée CLI, et pas uniquement la coroutine de complétion.
- **Contrat gateway / front** : `workspace/fingerprint` renvoyait les propriétés internes d'un `JsonNode` Jackson 2 via le convertisseur HTTP Jackson 3. Le contrôleur renvoie maintenant une map JSON, avec le champ `fingerprint` attendu par le contrôle avant remédiation. Une assertion utilise le véritable serveur HTTP Spring.
- **Diagnostic fournisseur** : les refus de clé, quota et modèle reçoivent des codes distincts, au lieu de suggérer systématiquement une panne Python. Les réponses privées du fournisseur restent masquées ; seuls des codes autorisés sont transmis.

## Résultats exécutés

| Contrôle | Résultat |
|---|---|
| `python tools/build.py --task Build` | SUCCESS, version 0.3.1, sortie 0 |
| Maven `clean verify` | 58 tests Java, 0 échec |
| Tests Python du build | 26 tests, 0 échec |
| Compilation, lint et tests Node | SUCCESS, 26 tests |
| Interface Playwright | 9 tests réussis, dont l'envoi Entrée jusqu'au contrôleur et au client HTTP |
| Tests HTTP mockXray | 3 tests réussis |
| Contrat Vibe embarqué | Réponse et outil via SDK réel ; 6 cas CLI réussis : succès, 401, 429, 404, modèle invalide 400, erreur serveur 500 |
| Runtime natif du VSIX → moteur/gateway → Docker | 10 contrôles réussis, 0 échec |
| Véritable hôte d'extensions VS Code, profil Test | 9 contrôles réussis, processus terminé avec le code 0 |
| Installation privée | `ods-local.ods-docker-manual@0.3.1` ; manifeste et 7 fichiers critiques conformes au VSIX |
| Configuration installée | Identique au profil administré renouvelé ; modèle `mistral-medium-3-5` |
| JAR depuis `suiteInstall/bin` | Démarrage moteur, santé gateway et refus anonyme réussis |

Les dix contrôles natifs couvrent les deux projets GitLab autorisé/refusé, leurs politiques signées, le contrôle d'accès anonyme, le fingerprint, un SBOM CycloneDX Maven réel, le rapport mockXray et une analyse Maven/SonarScanner/SonarQube réelle. Le défaut fingerprint qui faisait échouer le banc 0.3.0 ne se reproduit plus.

Le scénario du profil Test active l'extension réellement installée puis utilise ses classes compilées `OdsView`, `GatewayClient` et `EngineClient`. Il vérifie l'initialisation, le contrat fingerprint, une réponse IA, une lecture de `pom.xml` par `files_read`, ainsi que les erreurs 401/429/404 jusqu'aux messages destinés au front. Ces scénarios utilisent six échanges HTTP synthétiques avec le SDK Vibe réel. La capture de présentation et le stockage de ce scénario sont des doubles de test ; les tests DOM Playwright restent une validation distincte de l'affichage dans VS Code.

## Appel Mistral réel limité

Une unique soumission courte a traversé le client natif du profil Test, le gateway installé, le bridge Python et Vibe vers Mistral Medium 3.5. La réponse attendue `ODS_OK` a été reçue, avec l'état `SUCCEEDED`.

- Tokens d'entrée : **1 310**.
- Tokens de sortie : **4**.
- Total déclaré par le fournisseur : **1 314**.
- Aucune boucle d'outils ni relance de génération réelle.

Cet essai confirme la validité de la connexion, de la clé et du modèle à cet instant. Il ne constitue pas une qualification exhaustive des réponses du modèle, des longues conversations, des écritures, des MR ou des releases.

## Reproduction

Les services doivent être démarrés et la politique encore valide. `python TestDOKERMan/provision.py` renouvelle la politique, valable moins de 24 heures, sans écraser les clones existants.

```powershell
$env:JAVA_HOME = (Resolve-Path TestDOKERMan/.local/jdk).Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
node TestDOKERMan/native-smoke.cjs TestDOKERMan/.local/extensions/ods-local.ods-docker-manual-0.3.1
# Fermer la fenêtre Test avant le test de l'hôte.
python TestDOKERMan/profile_test.py TestDOKERMan/.local/extensions/ods-local.ods-docker-manual-0.3.1
python TestDOKERMan/vscode.py open
```

Le test du profil ne fait aucun appel fournisseur réel par défaut. Ajouter `--real-mistral` uniquement pour autoriser une soumission réelle supplémentaire. Les preuves privées se trouvent dans `.local/native-smoke-result.json`, `.local/profile-smoke-result.json` et `.local/real-mistral-result.json`. Les journaux privés ne sont pas publiés.

## Livraison, Git et limites

Version : **0.3.1**. VSIX : `.artifacts/ods-docker-manual-0.3.1.vsix`. Livraison autonome privée : `TestDOKERMan/suiteInstall-0.3.1.zip`, contenant `suiteInstall/`, ses deux JAR, le VSIX, les configurations expurgées, la documentation, VERSION, BUILD.json et SHA256SUMS. `BUILD.json` indique le commit source exact du package.

Sauvegarde distante préalable : `backup/chore/docker-manual-tests-0.3.0-20261031015258`, sur le commit `1bb601a`. Branche de correction : `fix/vibe-runtime-and-gateway`. Le commit applicatif est `7e1392a` ; les commits suivants ne modifient que le banc et sa documentation. Le rapport final indique le dernier commit et le résultat du push.

Le package privé contient les credentials administrés selon le mécanisme existant : il reste ignoré par Git et n'est pas publié. Aucun merge sur main, tag ou GitHub Release n'est effectué pour cette livraison locale. Les résultats de CI distante sont indiqués séparément dans le rapport final.

Aucun échec ne reste dans les contrôles initiaux exécutés. L'authenticité d'une détection JFrog Xray n'est pas vérifiée : le service est volontairement un mock. L'analyse de projets autres que le projet Java de démonstration, les MR et la publication de releases n'ont pas été rejouées contre les services réels. Une écriture réelle est maintenant couverte par le parcours ci-dessous. Les avertissements existants H2/Flyway, Mockito et VS Code n'ont pas empêché la réussite des tests.


## Parcours Quality dans la vraie interface — 31 octobre 2026

Le profil `Test` a été ouvert dans une instance isolée de VS Code Insiders 1.145.0, avec l'extension privée 0.3.1 déjà construite. Cette instance a permis de piloter la fenêtre malgré une erreur d'identification de l'installation portable stable et une mise à jour bloquante de l'installation principale. Le code de l'extension n'a pas été modifié pendant ce parcours.

1. **Analyser dans Quality** a exécuté le build Maven et une analyse sur le vrai SonarQube Docker. Le rapport contenait une seule issue `java:S106`, dans `src/main/java/local/manual/Calculator.java`, ligne 6.
2. **Corriger les issues** a lancé une tâche Mistral réelle. La première réponse a décrit une solution sans proposer de diff. Une consigne complémentaire en mode Agent a demandé l'utilisation de `changes_propose` et du logger standard Java, sans nouvelle dépendance.
3. **Mistral Medium 3.5** (`mistral-medium-3-5`) a généré le contenu de `Calculator.java`. Le diff a été relu puis appliqué depuis la confirmation native de l'extension. Le calcul `left + right` et le test existant sont préservés ; `System.out.println` est remplacé par `LOGGER.info` et des commentaires Javadoc ont été ajoutés. Le moteur a créé un checkpoint récupérable.
4. **Analyser à nouveau dans Quality** a exécuté `mvn -B -ntp verify`, puis SonarScanner Maven. Le rapport affiché contient `issues: []` et une Quality Gate `OK`. La lecture de l'API Sonar confirme **0 issue ouverte**. Certaines conditions de la gate sont ignorées par Sonar sur ce petit projet ; cela ne constitue pas une preuve de couverture exhaustive.
5. Le rapport Surefire confirme **1 test, 0 échec, 0 erreur, 0 test ignoré**. Le JAR `target/demo-1.0.0.jar` a été généré et son bytecode contient bien l'appel à `java.util.logging.Logger.info`.

La correction fonctionnelle a été écrite par Mistral via l'extension, sans édition manuelle du fichier Java par l'agent de pilotage. Aucun appel Mistral n'a été utilisé pour les scans ni pour la vérification finale. La première réponse du modèle a nécessité une relance explicite : le scénario ne démontre pas une correction complète en un seul clic.

Deux analyses intermédiaires ont échoué lors de lancements qui se chevauchaient pendant le pilotage partagé. Une relance unique a réussi avant la correction, puis le scan après correction a réussi. Aucun défaut de l'extension n'a été corrigé dans cette étape ; la gestion de clics concurrents et la continuation autonome du modèle restent des points à améliorer. Le diff Java généré contient trois lignes de Javadoc avec espaces finaux, signalées par `git diff --check` et conservées sans retouche manuelle.

Le clone de démonstration dispose de la sauvegarde GitLab `backup/develop-1.0.0-20261031022516` et de la branche `fix/sonar-quality-manual`. La documentation du banc dispose de la sauvegarde GitHub `backup/fix/vibe-runtime-and-gateway-0.3.1-20261031024501`.

La vidéo privée `.local/recordings/quality-mistral-final.mp4` enregistre uniquement la fenêtre Test, sans audio, en 1920 × 1008 à 10 images/s pendant 7 min 27,8 s. Le décodage intégral a réussi ; les images de contrôle et l'écran final ont été relus. Elle montre la génération du diff, son application et le rapport final sans issue. Les tentatives et temps d'attente sont conservés. La vidéo, les preuves JSON et les outils de capture restent ignorés par Git. Aucun nouvel artefact d'extension, tag ou release n'est créé pour ce test ; la livraison privée 0.3.1 existante reste inchangée.

## Correction automatique 0.3.2 — 2026-10-31

Le bouton **Corriger les issues** de Quality et Security sélectionne directement le mode Agent. Valider les issues sélectionnées autorise leur correction : Mistral génère les fichiers, le moteur les applique avec checkpoint, puis le diff s’ouvre. Il n’y a plus de choix de mode, de consigne complémentaire ni de confirmation du patch. Les commandes conservent leurs autorisations natives. Les réponses sans changement peuvent être relancées deux fois au maximum ; un échec ou une absence de patch est signalé.

Le test réel a utilisé un clone séparé `demo-automatic`, sauvegardé sur GitLab avant correction. Depuis l’interface Test : analyse Sonar, sélection de l’issue `java:S106`, clic sur correction, puis nouvelle analyse. **Aucun message complémentaire n’a été saisi dans le chat.** Mistral Medium 3.5 a ajouté `slf4j-api:2.0.16` dans `pom.xml` et remplacé `System.out.println` par `logger.info` dans `Calculator.java`. Les deux diffs sont apparus après écriture, avec deux checkpoints. Aucun fichier Java du test n’a été corrigé manuellement par l’agent de pilotage.

La nouvelle analyse affiche **0 issue ouverte**, une gate **OK** et l’empreinte `09e6bfa0f2772a3b69522d99b4a39d479059e9f457780bff28cd01d05cfdd51b`. Le build exécuté par Quality inclut `mvn -B -ntp verify` ; Surefire confirme **1 test, 0 échec, 0 erreur, 0 ignoré**. Le JAR de 2 283 octets a aussi été exécuté avec sa dépendance SLF4J : `add(2,3)` retourne `5`. Certaines conditions de la gate sont ignorées sur ce petit projet ; ce résultat ne prouve pas une couverture exhaustive. Le clone corrigé est publié sur le GitLab local dans `fix/automatic-remediation-test`, commit `c53a890`. Quatre lignes de commentaires générés ont des espaces finaux, conservés sans retouche.

Le premier essai a révélé que l’outil de commandes de l’agent autorisait `mvnw`, mais refusait Maven installé (`mvn`). Le wrapper proposé ensuite n’existait pas dans le projet et son exécution a été annulée. Ce défaut a été corrigé dans le moteur après la capture : `mvn` est maintenant autorisé avec la même protection contre les métacaractères, et l’adaptation Windows est conservée. Un nouveau test exécute réellement `mvn --version` sans wrapper et vérifie le refus du chaînage et d’un shell libre. La validation du code généré visible dans la vidéo provient du nouveau scan Quality ; elle ne doit pas être présentée comme un succès du premier appel de commande de l’agent.

Les tests automatisés couvrent les deux sources SONAR/XRAY, la relance après une réponse textuelle, l’application avant ouverture du diff, les conflits, l’annulation et les corrections concurrentes. Le test dans l’hôte VS Code 0.3.2 a réussi avec **8 contrôles et 6 appels HTTP simulés**, sans appel fournisseur réel. Security a été vérifié avec les doubles de test et le mock Xray ; aucune correction Security avec Mistral réel ni instance JFrog réelle n’est revendiquée.

Après la correction Maven, le build complet a été relancé : **59 tests Java, 26 Python, 36 tests natifs Node et 11 tests Playwright réussis**, ainsi que le démarrage des JAR et le refus HTTP des requêtes anonymes. Le client natif compilé a également exécuté `process.start` puis `process.output` via le nouveau moteur Java pour `mvn -B -ntp verify` dans `demo-automatic` : **code 0 et BUILD SUCCESS**, sans appel Mistral. La preuve privée est `.local/maven-agent-smoke.json`. Les trois tests HTTP du mock Xray ont aussi réussi.

La vidéo `.local/recordings/quality-automatic-0.3.2.mp4` contient **4 615 images à 10 images/s**, soit **7 min 41,5 s**, en 1920 × 1008, sans audio et limitée à la fenêtre Test. Son décodage intégral et les images de contrôle ont été vérifiés. Elle conserve les attentes et la tentative Maven décrite ci-dessus. Les preuves techniques sont dans `.local/automatic-ui-validation.json`, ignoré par Git.

Branche applicative : `fix/automatic-issue-remediation`. Sauvegardes GitHub : `backup/fix/vibe-runtime-and-gateway-0.3.1-20261031025924` avant le parcours automatique et `backup/fix/automatic-issue-remediation-0.3.2-20261031032136` avant la correction Maven. La livraison privée reste locale ; aucun merge vers main, tag ni GitHub Release n’est créé pour ce banc.


## Validation 0.5.0 dans Test1 ? 31 octobre 2026

Le profil isol? `Test1` utilise le clone `test1-corrections`, avec SonarQube, GitLab et mockXray locaux, et Mistral Medium 3.5. Le nombre maximal de tentatives est configurable de 1 ? 100 (valeur initiale 3). La valeur 2 saisie dans Quality a ?t? conserv?e apr?s rechargement de la fen?tre. Les tests automatis?s v?rifient le respect de cette limite et le refus des valeurs invalides.

Le parcours r?el Quality a corrig? `java:S106` dans Calculator : Mistral a remplac? la sortie console par un logger, sur une branche d?di?e. Compilation et test ont r?ussi, le nouveau scan Sonar ne contenait plus l?anomalie et le diff a ?t? ouvert apr?s validation. Le rapport PDF a ?t? export? depuis le bouton natif. Le premier export a r?v?l? des chemins Windows ?chapp?s dans les logs ; leur masquage a ?t? corrig? et test?. Cette premi?re preuve PDF priv?e a ?t? expurg?e apr?s export.

Le parcours de couverture a r?v?l? un d?faut IPC Windows : les accents du correctif ?taient alt?r?s par l?encodage de stdout, puis le contr?le d?empreinte refusait l?application. Les r?ponses du moteur sont d?sormais explicitement UTF-8. Un test passe volontairement un PrintStream Windows-1252, v?rifie les caract?res fran?ais et japonais, puis applique le correctif avec son empreinte intacte. Les erreurs d?outil sont affich?es et conserv?es dans le rapport ; une explication du mod?le sans fichier appliqu? reste ? non impl?ment? ?.
