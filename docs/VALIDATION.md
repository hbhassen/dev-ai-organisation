# Validation de la version 0.2.0

Date : 29 octobre 2026. Cette livraison est une version de développement. Les écarts fonctionnels du référentiel sont listés dans [CONFORMITE.md](CONFORMITE.md), sans assimilation d'un build réussi à une recette complète.

## Vérifications exécutées

- Java 21 / Maven Wrapper 3.9.11 : compilation et **46 tests réussis** (4 contrats, 21 moteur, 21 gateway), aucun échec ni test ignoré. Les résultats détaillés figurent dans les rapports Surefire du build et de la CI du commit livré.
- TypeScript 5.9.3 : compilation, contrôle des commentaires par AST et **13 tests Node réussis**, dont révocation de racine fermée malgré une panne d'annulation distante.
- Python 3.11 : contrôle des docstrings/exigences et **6 tests réussis** (3 sécurité du runner et 3 profils de compilation).
- Webview : **6 tests Chromium réussis**, navigation clavier, cinq onglets/icônes, largeurs 330/900 px, composer, absence de double soumission, brouillon refusé, XSS traité comme texte, pièces jointes, pagination et dispatch de tous les onglets. Rendus clair/sombre inspectés visuellement.
- VSIX : génération réelle avec `vsce`, inspection des entrées, moteur embarqué. Un test dans un véritable extension host VS Code portable a activé l'extension, interrogé le moteur Java, inspecté le workspace et vérifié le refus d'un chemin sortant.
- JAR : démarrage réel du moteur et de la gateway, réponse HTTP de santé et refus HTTP d'une API métier anonyme. Le packaging répète ce contrôle avec les JAR copiés dans `suiteInstall/bin`.
- Gouvernance : préparation d'un bundle, génération de clés de test, signature Ed25519 et validation effective des recommandations. Les clés de test sont hors Git et hors package.

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

`suiteInstall/BUILD.json` identifie le commit et l'éventuel état modifié des sources. Un package avec `sourceDirty: true` est un essai local, pas un artefact de release. Les artefacts de CI sont construits depuis le commit publié. Les branches `main` et le tag de release restent soumis aux conditions de validation ; la version 0.2.0 est publiée comme préversion de développement, avec les écarts de recette explicitement conservés. L’instantané `dev/toreprise` est distinct du commit source tagué et reprend ses artefacts sans les recompiler.
