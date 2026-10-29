# Validation de la version 0.1.0

Date : 29 octobre 2026. Cette livraison est une version de développement. Les écarts fonctionnels du référentiel sont listés dans [CONFORMITE.md](CONFORMITE.md), sans assimilation d'un build réussi à une recette complète.

## Vérifications exécutées

- Java 21 / Maven Wrapper 3.9.11 : compilation et **42 tests réussis** (4 contrats, 21 moteur, 17 gateway), aucun échec ni test ignoré. Les résultats détaillés figurent dans les rapports Surefire du build et de la CI du commit livré.
- TypeScript 5.9.3 : compilation, contrôle des commentaires par AST et **4 tests Node réussis**.
- Python 3.11 : contrôle des docstrings/exigences et **3 tests de sécurité du runner réussis** (archive, traversée et exclusion de secrets).
- VSIX : génération réelle avec `vsce`, inspection des entrées, moteur embarqué. Un test dans un véritable extension host VS Code portable a activé l'extension, interrogé le moteur Java, inspecté le workspace et vérifié le refus d'un chemin sortant.
- JAR : démarrage réel du moteur et de la gateway, réponse HTTP de santé et refus HTTP d'une API métier anonyme. Le packaging répète ce contrôle avec les JAR copiés dans `suiteInstall/bin`.
- Gouvernance : préparation d'un bundle, génération de clés de test, signature Ed25519 et validation effective des recommandations. Les clés de test sont hors Git et hors package.

## Projets réellement générés

| Stack | Générateur exécuté | Validation observée |
|---|---|---|
| Java | Spring Initializr, Spring Boot 4.1.2, Java 21, Maven | `mvnw clean verify` réussi, **1 test** de contexte, JAR exécutable généré |
| Angular | CLI officielle 21.2.5 | Installation npm, build et `npm test -- --watch=false` réussis, **2 tests** |
| Python | Package PyPA avec `pyproject.toml` / setuptools | **2 tests unittest** réussis ; wheel 0.1.0 effectivement construit |
| .NET | SDK officiel 10.0.403, WebAPI + xUnit | Build de solution et **2 tests HTTP** réussis ; SDK isolé de l'installation système |

Les validations de starters sont distinctes des tests de l'application ODS. Les téléchargements et répertoires d'essai restent ignorés. Les dépendances transitives effectives figurent dans les locks des projets générés ; la validation d'une génération n'est pas une garantie de disponibilité future d'un registre public.

## Tests isolés et limites

Les tests de gateway valident HTTP local, migrations, idempotence, quotas concurrents, propriété des jobs, chiffrement, reprises de baux, boucle d'outils, classement des remédiations et contrats Mistral/Sonar. Les services tiers y sont remplacés par des réponses de test explicites. Ces doubles ne sont pas activables comme faux mode de production.

SSO, modèle Mistral approuvé, GitLab, SonarQube, JFrog Xray et runner de l'organisation : **NON VÉRIFIABLES dans cet environnement**, faute d'instances et d'identifiants réels. Les URL d'exemple restent remplaçables. La CI Linux a réellement réussi avec PostgreSQL 16.10, les 42 tests Java, les tests Python et TypeScript ; voir [l'exécution de référence](https://github.com/hbhassen/dev-ai-organisation/actions/runs/40797257318). Le premier job Windows a révélé une assertion comparant un chemin temporaire court à sa forme canonique ; l'assertion est corrigée pour comparer les chemins réels, sans supprimer les contrôles de traversal. Aucun test de charge, pentest complet, restauration de production ou qualification de lecteur d'écran n'est revendiqué.

## Reproduction et livraison

`build.bat` exécute les contrôles, le réacteur Maven, la compilation/tests TypeScript et les démarrages réels. `generate-vsix.bat` ajoute le VSIX. `package.bat` construit en plus l'archive autonome et ses empreintes. Chaque erreur est propagée par un code retour non nul.

`suiteInstall/BUILD.json` identifie le commit et l'éventuel état modifié des sources. Un package avec `sourceDirty: true` est un essai local, pas un artefact de release. Les artefacts de CI sont construits depuis le commit publié. Les branches `main` et le tag de release restent soumis aux conditions de validation ; une release stable n'est pas déclarée tant que les écarts d'acceptation restent ouverts.
