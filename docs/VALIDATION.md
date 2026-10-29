# Validation de la version 0.2.1

Date : 29 octobre 2026. Cette livraison est une version de développement. Les écarts fonctionnels du référentiel sont listés dans [CONFORMITE.md](CONFORMITE.md), sans assimilation d'un build réussi à une recette complète.

## Vérifications exécutées

- Java 21 / Maven Wrapper 3.9.11 : compilation et **46 tests réussis** (4 contrats, 21 moteur, 21 gateway), aucun échec ni test ignoré. Les résultats détaillés figurent dans les rapports Surefire du build et de la CI du commit livré.
- TypeScript 5.9.3 avec `@types/vscode` 1.95.0 : compilation, contrôle des commentaires par AST et **13 tests Node réussis**, dont révocation de racine fermée malgré une panne d'annulation distante.
- Python 3.11 : contrôle des docstrings/exigences et **14 tests réussis** (3 sécurité du runner, 3 profils de compilation, 6 contrôles du build/packaging et 2 contrôles du lanceur de gateway).
- Webview : **6 tests Chromium réussis**, navigation clavier, cinq onglets/icônes, largeurs 330/900 px, composer, absence de double soumission, brouillon refusé, XSS traité comme texte, pièces jointes, pagination et dispatch de tous les onglets. Les rendus clair/sombre avaient été inspectés visuellement lors de la version 0.2.0 ; ce correctif ne modifie pas l'interface.
- VSIX : génération réelle avec `vsce`, inspection des entrées, moteur embarqué. Installation et test `vscode-extension/test/host-smoke.cjs` réussis dans les véritables extension hosts **VS Code 1.95.0 et 1.95.2 sous Windows x64**, avec Node embarqué 20.18.0 : activation, commande ODS, IPC Java, inspection du workspace, lecture d'un fichier et refus d'un chemin sortant. Les profils et répertoires d'extensions sont isolés de l'installation habituelle.
- JAR : démarrage réel du moteur et de la gateway, réponse HTTP de santé et refus HTTP d'une API métier anonyme. Le packaging répète ce contrôle avec les JAR copiés dans `suiteInstall/bin`.
- Gouvernance : le contrôle indépendant du socle 0.1.0 avait préparé un bundle, généré des clés de test, signé en Ed25519 et validé les recommandations. Les tests automatisés de signatures et de bundles sont réexécutés dans les 46 tests Java ci-dessus. Les clés de test sont hors Git et hors package.

## Compatibilité VS Code

L'ancien VSIX 0.2.0 exigeait `^1.96.0` ; son refus d'installation a été reproduit sur VS Code 1.95.0. Le VSIX 0.2.1 déclare `^1.95.0` et sa compilation utilise les définitions d'API de cette version minimale. L'installation accepte ainsi les versions de la série 1.x à partir de 1.95.0, dont 1.95.2. Le même test d'installation et d'activation a aussi réussi avec la distribution portable VS Code 1.144.0 (Node embarqué 24.22.1). Les essais ci-dessus ne constituent pas une qualification exhaustive de chaque version intermédiaire ou future. Les versions antérieures à 1.95.0 et VS Code 2.x restent hors plage.

Pour reproduire le test natif, installer le VSIX dans un répertoire d'extensions isolé d'une distribution officielle VS Code de la version cible, puis lancer cette distribution avec `--extensionDevelopmentPath=<EXTENSION_INSTALLEE>`, `--extensionTestsPath=<PROJECT_HOME>/vscode-extension/test/host-smoke.cjs`, `--user-data-dir=<PROFIL_TEST>`, `--extensions-dir=<EXTENSIONS_TEST>` et un workspace de test contenant un `pyproject.toml`. Le workspace doit être approuvé pour tester les outils Java. La CI existante compile avec les API 1.95.0 ; ces essais sur des hôtes historiques sont des vérifications locales supplémentaires.

## Projets réellement générés lors du socle 0.1.0 (générateurs inchangés)

| Stack | Générateur exécuté | Validation observée |
|---|---|---|
| Java | Spring Initializr, Spring Boot 4.1.2, Java 21, Maven | `mvnw clean verify` réussi, **1 test** de contexte, JAR exécutable généré |
| Angular | CLI officielle 21.2.5 | Installation npm, build et `npm test -- --watch=false` réussis, **2 tests** |
| Python | Package PyPA avec `pyproject.toml` / setuptools | **2 tests unittest** réussis ; wheel 0.1.0 effectivement construit |
| .NET | SDK officiel 10.0.403, WebAPI + xUnit | Build de solution et **2 tests HTTP** réussis ; SDK isolé de l'installation système |

Les validations de starters sont distinctes des tests de l'application ODS. Les téléchargements et répertoires d'essai restent ignorés. Les dépendances transitives effectives figurent dans les locks des projets générés ; la validation d'une génération n'est pas une garantie de disponibilité future d'un registre public.

## Tests isolés et limites

Les tests existants restent exécutés. `OrganisationIntegrationTest` ajoute quatre scénarios avec de vrais échanges HTTP/TLS et les contrôleurs Spring :

- découverte OIDC, validation RSA/audience/identité GitLab, politique Ed25519 ;
- résolution projet, capacités, prompts, agent avec appel d’outil, streaming SSE Mistral, reprise du tour, quotas et suppression du checkpoint ;
- Sonar paginé, chemins normalisés, Xray normalisé et deux pipelines de scan attachés au commit ;
- création de MR avec identité déléguée, refus GitLab/Xray et panne fournisseur Mistral.

Les fixtures GitLab, LLM, Sonar et Xray sont servies par un serveur HTTPS éphémère sur loopback. Un certificat temporaire est approuvé exclusivement dans le JVM de test, puis la confiance initiale est restaurée. Les clés privées sont générées pour le test, sans certificat invalide accepté ni mode anonyme ajouté au produit. Les profils/branding sont testés avec une credential synthétique dont l’absence dans le staging VSIX est vérifiée.

SSO, modèle Mistral approuvé, GitLab, SonarQube, JFrog Xray et runner de l’organisation : **NON VÉRIFIABLES sur les instances réelles**, leurs URL et identifiants devant être fournis ultérieurement. Le mock ne prouve ni la disponibilité ni la compatibilité particulière d’une instance. Le flux PKCE complet dans un navigateur connecté au vrai SSO, la charge, le pentest, la restauration de production et le lecteur d’écran restent non qualifiés.

La CI comporte un job Windows de livraison et un job Linux avec PostgreSQL 16.10 pour les tests persistants existants ; le nouveau scénario d’intégration utilise sa propre base H2 isolée. Les résultats applicables à la livraison sont ceux de la CI associée au commit de `BUILD.json`, accessibles dans les [exécutions GitHub Actions](https://github.com/hbhassen/dev-ai-organisation/actions).

## Reproduction et livraison

`build.bat` exécute les contrôles, le réacteur Maven, la compilation/tests TypeScript et navigateur et les démarrages réels. `generate-vsix.bat` ajoute le VSIX. `package.bat` construit en plus l'archive autonome et ses empreintes. `script.bat <profil.json>` exécute toute cette chaîne avec surcharge organisationnelle. Chaque erreur est propagée par un code retour non nul.

Les scripts batch invoquent Python ou la CLI VS Code directement. Le wrapper Maven Windows utilise lui aussi Python et vérifie le SHA-256 déclaré dans les propriétés existantes ; aucun de ces scripts n'appelle PowerShell. Les nouveaux tests vérifient les codes d'échec réels des sous-processus, le changement de commit pendant un build, la conservation des anciens livrables, le cas d'un dossier verrouillé, les archives Maven corrompues ou traversantes, la version et l'empreinte du moteur dans le VSIX, ainsi que les valeurs de configuration littérales et les références externes manquantes.

`build.bat` et `generate-vsix.bat` ont été exécutés directement sous `cmd.exe` avec succès après cette migration. Le nouveau `start-gateway.bat` a aussi démarré le vrai JAR depuis un dossier contenant des espaces, avec un fichier `--config` externe et une référence `${VARIABLE}` pour le port : santé/version 0.2.1 et refus anonyme vérifiés. Les processus de ces essais sont isolés puis arrêtés.

`suiteInstall/BUILD.json` identifie le commit et l'éventuel état modifié des sources. Un package avec `sourceDirty: true` est un essai local, pas un artefact de release. Les artefacts de CI sont construits depuis le commit publié. Les branches `main` et le tag de release restent soumis aux conditions de validation ; le correctif 0.2.1 conserve les écarts de recette de la préversion 0.2.0. L’instantané historique `dev/toreprise` reste associé à 0.2.0 et ne constitue pas la livraison de ce correctif.

À la demande explicite de l'utilisateur, `script/vsix` conserve aussi le dossier `suiteInstall/` du correctif. Le commit source est réalisé avant sa reconstruction ; un second commit enregistre la livraison vérifiée. La correspondance se contrôle avec `BUILD.json.sourceCommit` et `SHA256SUMS`. Ce commit contenant des binaires ne doit pas être fusionné dans les branches source.
