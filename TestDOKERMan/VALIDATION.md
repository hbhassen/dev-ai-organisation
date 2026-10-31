# Validation du banc et des corrections ODS 0.3.1 — 31 octobre 2026

Les deux défauts reproduits ont été corrigés à la demande de l'utilisateur. La version 0.3.1 est installée dans le profil VS Code `Test`. Les validations ci-dessous ont réellement été exécutées sur Windows. Un seul appel Mistral réel a été effectué après autorisation explicite ; les autres appels fournisseur ont été simulés.

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

Aucun échec ne reste dans les contrôles exécutés. L'authenticité d'une détection JFrog Xray n'est pas vérifiée : le service est volontairement un mock. L'analyse de projets autres que le projet Java de démonstration, les actions d'écriture, les MR et la publication de releases n'ont pas été rejouées contre les services réels. Les avertissements existants H2/Flyway, Mockito et VS Code n'ont pas empêché la réussite des tests.
