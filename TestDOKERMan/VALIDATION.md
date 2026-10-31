# Validation du banc local 0.3.0 — 31 octobre 2026

Les sources existantes du moteur, du gateway et de l'extension sont inchangées. Ce banc utilise la version applicative 0.3.0 ; il ne constitue pas une nouvelle release. Aucun appel de génération Mistral n'a été effectué par nos validations.

## Résultats réellement exécutés

| Contrôle | Résultat |
|---|---|
| `python tools/build.py --task Build` | SUCCESS, code retour 0 |
| Maven `clean verify`, modules existants | 56 tests Java réussis, 0 échec |
| Tests Python du build existant | 24 réussis |
| Compilation/lint et tests Node de l'extension | SUCCESS, 26 tests réussis |
| Interface Playwright existante | 9 tests réussis |
| HTTP mockXray | 3 tests réussis : authentification, erreurs, cycle asynchrone |
| Projet Java de démonstration, Maven `verify` | SUCCESS, 1 test JUnit réussi |
| GitLab réel, clones HTTPS et politique signée | SUCCESS, projets autorisé/refusé ; octets publiés identiques aux octets signés |
| Clients compilés de l'extension vers moteur Java et gateway réels | IPC, résolution des projets, droits et refus des accès anonymes réussis |
| SBOM depuis les sources locales via le gateway | SUCCESS, CycloneDX Maven réel |
| Graph Scan Xray via le gateway | SUCCESS, résultat synthétique `XRAY-MOCK-0001` normalisé |
| SonarQube via le gateway | SUCCESS au contrôle final : Maven verify, SonarScanner, Compute Engine et lecture du rapport |
| Contrat HTTP `workspace/fingerprint` | **FAILED : défaut applicatif existant reproduit** |
| VSIX privé et démarrage des JAR depuis `suiteInstall/bin` | SUCCESS, existence/taille/intégrité, moteur et santé/authentification du gateway contrôlés |
| Installation VS Code | `ods-local.ods-docker-manual@0.3.0` installé dans `Test`, fenêtre ouverte et activation confirmée dans le journal de l'hôte d'extensions |
| `start.ps1 -Open` | SUCCESS : services prêts en HTTPS, politique renouvelée et profil Test ouvert |
| Appel Mistral Medium 3.5 | NON EXÉCUTÉ, réservé au test manuel |

Le build existant passe, mais le nouveau test d'intégration **ne passe pas intégralement**. Il conserve un code retour 1 lorsqu'une vérification échoue. Les assertions ne sont ni désactivées ni masquées. Les autres contrôles indépendants sont poursuivis pour établir un diagnostic complet.

Dernier résultat natif : **9 contrôles réussis, 1 échec** (`workspace/fingerprint`).

Les avertissements existants sur H2/Flyway, l'agent Mockito et les métadonnées Maven Shade n'ont pas fait échouer le build.

Un premier passage Sonar par le gateway a retourné `LOCAL_SCAN_FAILED`. L'analyse directe, l'exécution du script exact livré dans le VSIX, puis la relance complète par le client natif ont réussi. La cause précise de ce premier échec n'a pas été isolée ; aucun test ni code applicatif n'a été changé pour le masquer.

## Défaut de communication reproduit

Reproduction avec le VSIX installé :

```powershell
$env:JAVA_HOME = (Resolve-Path TestDOKERMan/.local/jdk).Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
node TestDOKERMan/native-smoke.cjs TestDOKERMan/.local/extensions/ods-local.ods-docker-manual-0.3.0
```

Le script n'appelle aucun endpoint IA. Il enregistre les réussites et échecs dans `.local/native-smoke-result.json`.

`GET /api/v1/workspace/fingerprint?projectId=2` renvoie HTTP 200 avec un objet du type `{"nodeType":"OBJECT","object":true,"containerNode":true,...}`, alors que le front attend `{"fingerprint":"<SHA256>"}`. Le contrôle avant remédiation dans `vscode-extension/src/view.ts` compare ce champ inexistant à celui du rapport : il refuse donc la correction.

Le contrôleur retourne directement un `com.fasterxml.jackson.databind.JsonNode` (Jackson 2), tandis que le JAR contient le convertisseur Spring Boot/Jackson 3. C'est la cause déduite du code et du format observé. La [documentation Spring Boot](https://docs.spring.io/spring-boot/4.0/reference/features/json.html) confirme que Jackson 3 est préféré et que l'auto-configuration Jackson 2 nécessite son module dédié, absent du JAR inspecté. Aucune dépendance ni source applicative n'a été modifiée pour contourner ce défaut.

## Préparation et livraison

- GitLab CE 18.2.0, SonarQube Community 26.9.0.129388, nginx et mockXray sont démarrés dans le projet Compose `ods-manual`.
- Les connexions GitLab/Sonar/mock passent par HTTPS avec vérification de l'autorité locale. Les ports publiés sont limités à loopback.
- Comptes GitLab : `serviceExtebtion` et `user1`. Projets privés : `ods-manual/governance`, `ods-manual/demo-allowed`, `ods-manual/demo-denied`.
- Modèle configuré : `mistral-medium-3-5`. Clé, quota et disponibilité distante NON VÉRIFIÉS par un appel réel.
- Une erreur de normalisation CRLF dans le nouveau script de publication a été corrigée : la signature porte désormais exactement sur les octets présents dans GitLab, et une relecture vérifie cette égalité.
- Java 21 et son magasin de confiance sont isolés sous `.local/jdk`. Le lanceur configure aussi le Java des terminaux du profil Test.
- Package local : `TestDOKERMan/suiteInstall-0.3.0.zip`, avec `suiteInstall/` à sa racine, VERSION 0.3.0, deux JAR et VSIX privé, configurations expurgées, documentation et sommes SHA-256.
- Ce package contient les credentials locaux dans le VSIX selon le mécanisme applicatif existant : **publication distante NON RÉALISÉE**. Validation sur une autre machine : NON VÉRIFIABLE ici.
- L'automatisation Windows a confirmé la fenêtre du profil Test, mais n'a pas pu lui associer durablement un contrôle UI à cause d'un conflit d'identifiant entre les deux installations VS Code. Le parcours visuel complet reste à effectuer manuellement ; les tests Playwright et les échanges natifs sont des validations distinctes.

## Git et documentation

Sauvegarde distante créée avant modification : `backup/hm/integration-0.3.0-20261031004407`. Branche de travail : `chore/docker-manual-tests`. Base applicative : `036f499`.

Le `suiteInstall/` historique a été retiré de l'index sur la branche de travail, sans supprimer les fichiers locaux ni modifier l'historique distant existant. Les nouveaux secrets, caches, clones et packages sont ignorés. Aucun merge sur main, tag ou release n'est effectué pour ce banc local.

Sources du banc : Compose/nginx, mockXray et tests, scripts de préparation/provisionnement/packaging/lancement, projet Java de démonstration et test d'intégration. Documentation mise à jour : `README.md`, `CHANGELOG.md`, `TestDOKERMan/README.md` et le présent bilan. Le commit final et le résultat du push sont indiqués dans le rapport de livraison.
