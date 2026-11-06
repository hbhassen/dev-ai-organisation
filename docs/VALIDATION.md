# Validation des versions ODS

## Choix des modèles 0.8.0 — 7 novembre 2026

Validation locale `py -3.12 tools/build.py --task Build` : **SUCCESS**. Maven `clean verify` : **78 tests** réussis (4 contrats, 27 moteur, 47 gateway), sans échec ni test ignoré. Python : **40 tests** ; Node : **74 tests** ; Chromium : **18 scénarios**, tous réussis. Compilation TypeScript et contrôles des commentaires Java/Python/TypeScript réussis. Version moteur 0.8.0, démarrage et santé gateway, refus des requêtes anonymes vérifiés depuis les JAR générés.

DEVAI-199 est couvert pour les listes signées de 1, 2 et 3 modèles, les alias étrangers à un projet, la révocation, les outils manquants, les quotas attribués au choix, les trois modes avec chaque fournisseur simulé, l'idempotence après outil, la migration des checkpoints et le refus d'une réaffectation de modèle. Les tests natifs et navigateur couvrent la nouvelle conversation lors d'un changement, les consentements réinitialisés, la provenance des anciennes reprises, le verrouillage pendant une tâche, la largeur 330 px et la compatibilité du profil texte sans outils.

Les fournisseurs de cette validation automatique sont simulés. Aucun troisième modèle réel n'a été qualifié. Les essais réels éventuels du profil privé sont consignés séparément des tests automatiques et ne changent pas cette limite. Le PDF d'architecture comporte 19 pages rendues et inspectées. Le service de contrôle graphique du poste est indisponible ; les tests Chromium ne constituent pas une vérification visuelle de l'hôte VS Code installé.

Le workflow de livraison reconstruit le package public depuis le commit propre et conserve `sourceCommit`, `sourceDirty`, version et profil dans `suiteInstall/BUILD.json`. Le profil privé du banc ne doit pas être publié.


## Correctif 0.7.1 — 6 novembre 2026

Validation locale exécutée avec `py -3.12 tools/build.py --task Build` : **SUCCESS**. Maven `clean verify` : **72 tests** réussis (4 contrats, 26 moteur, 42 gateway), aucun échec ni test ignoré. Python : **38 tests** ; Node : **69 tests** ; Chromium : **14 scénarios**, tous réussis. Compilation TypeScript et contrôles des commentaires Java/Python/TypeScript réussis. Démarrage des JAR 0.7.1, version moteur, santé gateway et refus des requêtes anonymes vérifiés.

Les nouveaux contrôles couvrent l’identité exacte dans les capacités HTTP, son transfert depuis le résultat du job jusqu’au DOM, l’historique après changement de fournisseur, les métadonnées absentes, le rendu de texte hostile, la largeur 330 px et le contexte vide. Annuler ou choisir des fichiers avant envoi ne déclenche aucune génération ; les aperçus et les consentements continuent de protéger les transferts. Le PDF d’architecture a été régénéré et ses 18 pages inspectées avec une capture synthétique 0.7.1.

Aucun appel fournisseur réel n’a été exécuté pour ce correctif. Les générations sont simulées aux frontières HTTP ; les résultats OpenAI réels de 0.7.0 ci-dessous restent historiques. Les livraisons définitives sont fabriquées depuis le commit propre et portent leur provenance dans `suiteInstall/BUILD.json`.

## Historique 0.3.0

Date : 29 octobre 2026. Livraison de développement, profil de démonstration sans identifiants réels. La révision 1.4 des spécifications supprime le SSO du VSIX administré. Les paragraphes 0.2.1 ci-dessous sont conservés comme historique et ne décrivent pas son nouveau parcours de connexion.

## Contrôles exécutés pour cette évolution

- Java 21 / Maven : `verify`, **56 tests réussis**, aucun échec ni test ignoré (4 contrats, 23 moteur, 29 gateway). Les nouveaux tests couvrent l'accès local, le Graph Scan Xray, Sonar, les transports Vibe et le masquage des erreurs. Le packaging complet utilise `clean verify` et conserve ses rapports Surefire.
- Python : **24 tests réussis**, dont l'empreinte des sources non commitées, les commandes CycloneDX/Sonar des quatre stacks avec processus simulés, l'arrêt des descendants, les paramètres du runtime, les chemins longs Windows et le refus des fichiers dotenv accidentels.
- TypeScript compilé avec les API VS Code 1.95.0 ; **26 tests Node réussis** : contrôle Git avant connexion, refus d'accès, configuration administrée et masquage des valeurs sensibles, en plus des fonctionnalités antérieures.
- Interface Chromium : **9 scénarios réussis**. Le scénario Entrée utilise le vrai contrôleur natif et le client HTTP avec un LLM simulé ; Maj+Entrée, composition IME, double soumission, refus, sécurité de rendu et navigation sont également vérifiés.
- GitFlow : huit nouveaux tests Node traversent le vrai contrôleur, les réponses LLM simulées et le parcours de diff pour créer/mettre à jour les deux documents ; refus, panne fournisseur, cible interdite et suppression sont vérifiés. Deux scénarios Chromium supplémentaires vérifient les boutons, les droits distincts, le blocage pendant une tâche et le rendu aux largeurs 330/900 px. Les prompts embarqués et les deux actions de politique ont aussi leurs tests Java.
- Runtime : démarrage du véritable JAR de gateway avec le client natif, après une vérification Git simulée. Accès refusé avant création du processus, API locale refusée sans jeton de lancement, appel autorisé avec ce jeton, stockage natif de la clé persistante et arrêt du processus après révocation Git : **SUCCESS**.
- Vibe 3.0.3 : contrat exécuté avec la distribution et le SDK effectivement embarqués, en simulant uniquement le transport HTTP. Réponse texte, appels d'outils typés, modèle/URL/clé et usage mesuré : **SUCCESS**. Deux tests supplémentaires exécutent réellement le sous-processus Python depuis Java pour les modes API_KEY et CLI_TOKEN, sans fournisseur externe.
- Commentaires : contrôle des classes/méthodes Java, fonctions/classes Python et symboles TypeScript, avec références DEVAI connues. **49 fichiers Java et 16 fichiers Python** vérifiés ; contrôle TypeScript inclus dans `npm run lint`.
- VS Code **1.95.2 Windows x64** : installation réelle du VSIX 0.3.0 réussie dans un profil isolé ; activation dans le véritable extension host, commandes, IPC du moteur embarqué, lecture de fichier et refus de sortie du workspace vérifiés, code de sortie 0. Les tests natifs ne modifient pas l’installation habituelle.
- PDF : document d'architecture de **14 pages**, révision 1.4, régénéré et rendu page par page pour inspection visuelle. Schéma Git → gateway, interface réelle et exigences DEVAI-167 à DEVAI-181 présents.
- Batch : aucun appel PowerShell dans les `.bat`/`.cmd` du produit. Le commentaire historique du wrapper mentionne uniquement son absence.

## Portée de la recette

Les échanges métier sortants sont simulés aux frontières HTTP, Git et scanners. Les tests Java historiques des intégrations distantes et du mode OIDC autonome restent exécutés ; ce mode ne sert pas à la connexion du VSIX. La génération et les appels de scanners sont testés sur fixtures, sans prétendre avoir analysé quatre applications réelles avec les comptes de l'organisation.

GitLab privé, gouvernance signée de l'organisation, modèle et token Vibe réels, watch Xray et SonarQube réels : **NON VÉRIFIABLES**, faute de paramètres fournis. Un jeton CLI doit être accepté par l'URL Vibe compilée ; aucun flux de connexion ou rafraîchissement interactif n'est embarqué. La distribution Vibe est utilisée par l'adaptateur CLI ODS ; l'agent interactif `vibe --prompt` et ses outils autonomes ne sont pas exécutés.

Le package reste spécifique à sa plateforme et nécessite Java 21, Python 3.12 et les outils des stacks analysées. Les secrets embarqués sont masqués dans l'interface et les diagnostics, mais restent extractibles, conformément au choix explicite de distribution autonome. Les profils d'exemple ne contiennent que des placeholders.

## Reproduction

`script.bat config\organisations\demo\organisation.json` reconstruit la validation complète, le VSIX, les JAR et l'archive `suiteInstall-0.3.0.zip`. Le packaging vérifie l'inventaire et les empreintes du runtime, puis démarre aussi les JAR copiés dans la livraison. `suiteInstall/BUILD.json` indique le commit source exact ; `sourceDirty: true` désigne uniquement un essai local. La livraison définitive doit être reconstruite après le commit avec `sourceDirty: false`.

Les artefacts restent ignorés par Git. La CI Windows construit la suite ; la CI Linux qualifie son runtime Vibe et exécute les tests avec PostgreSQL. Consulter le résultat de la CI du commit livré avant toute promotion. Un build local ne vaut pas preuve de succès de CI ou de publication d'une release.

---

# Historique de validation 0.2.1

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


## Validation 0.7.0 — 6 novembre 2026

- Branche de travail : `feat/genLLM`. Ces résultats concernent les validations locales ; la release reste conditionnée aux contrôles de livraison.
- Réacteur Maven `clean verify` réussi après correction des capacités des projets sans droit IA : **71 tests Java** (4 contrats, 26 moteur, 41 gateway), aucun échec, erreur ou test ignoré. Le nouveau test HTTP conserve l’accès aux capacités sans alias et vérifie le refus de génération avant tout appel fournisseur.
- Build complet `tools/build.py --task Build` réussi après le correctif : compilation et lint TypeScript, **63 tests Node et 12 scénarios Chromium réussis**, démarrage des JAR, santé gateway et refus anonyme vérifiés.
- Tests Python : **38 tests réussis**, dont l’installation de la version courante et l’ouverture d’une nouvelle fenêtre Test ; contrôle des références de documentation réussi.
- Runtime Vibe 3.0.3 réellement empaqueté : génération, outils et usage Mistral/OpenAI avec HTTP simulé ; **6 cas CLI Mistral et 10 cas CLI OpenAI réussis**, incluant authentification, quota, modèle inconnu, panne, timeout, réponse malformée et redirection. Aucun appel fournisseur réel dans ces contrats.
- Le contrôle d’intégrité a détecté un bytecode généré par le test CLI. Les processus de qualification utilisent désormais `-B` et vérifient l’absence de `.pyc` dans le runtime. Le VSIX privé a ensuite passé la vérification complète des empreintes ; il contient des credentials locaux et ne constitue pas un artefact public. Le runtime contenant le contrôleur corrigé a ensuite passé le parcours natif Docker.
- DEVAI-197 : conservation de `JAVA_HOME` et de `PATH` vérifiée par les tests d’environnement, avec fixture JDK 25. Cette fixture ne constitue pas un build réel d’un projet utilisateur sous JDK 25.
- Banc Docker réel : accès GitLab et politique signée, moteur Java IPC, fingerprint, génération Maven CycloneDX et rapport mockXray réussis. Le premier scan Sonar échouait car le `JAVA_HOME` du terminal sélectionnait Java 17 pour un POM Java 21 ; le build et le scanner Sonar réels réussissent avec le JDK 21 du banc dans le seul environnement de test. Le parcours natif complet réussit ensuite : **10 contrôles, aucun échec**, y compris le projet dont l’IA est désactivée.
- **OpenAI réel : SUCCESS.** Après renouvellement de la clé et activation de la facturation API, une génération synthétique via la gateway et le runtime Vibe empaqueté a retourné le marqueur attendu : modèle `gpt-6-luna`, **977 jetons d’entrée et 10 de sortie, soit 987 jetons**. Le contexte était vide, les outils désactivés et la sortie plafonnée à 128 jetons. Aucun nouvel appel payant n’a été lancé après ce succès et aucun appel réel Mistral n’a été effectué. Le parcours global de cette tentative n’est pas déclaré réussi : une saturation mémoire a ensuite rendu Sonar, mockXray et un contrôle GitLab indisponibles. Les dix contrôles Docker avaient réussi lors du parcours séparé sans appel modèle décrit ci-dessus.
- PDF d’architecture : 18 pages rendues et inspectées visuellement.
- Confidentialité : les anciens fichiers privés du banc et son PDF contenant des credentials ont été retirés du suivi Git, conservés localement et ignorés. Le dernier audit de 249 fichiers n’a retrouvé aucune des signatures ou valeurs sensibles recherchées. Les anciennes révisions publiques demeurent inchangées ; les credentials anciennement publiés doivent être révoqués.
- CI du commit `6583281` : [Windows et Linux réussis](https://github.com/hbhassen/dev-ai-organisation/actions/runs/42145990700), avec PostgreSQL 16.10 côté Linux. L’archive publique de ce commit a été téléchargée sur le poste : identité propre du commit, version 0.7.0, profil `organisation-demo`, empreintes des binaires et inventaire complet du VSIX vérifiés. Le moteur et la gateway extraits du package ont ensuite passé le smoke local (version, santé et refus anonyme), sans appel modèle.
- La preuve du package public doit identifier le commit exact dans `suiteInstall/BUILD.json`, avec `sourceDirty: false` ; les résultats CI applicables sont ceux de ce commit dans GitHub Actions. Les livraisons privées ne doivent jamais être publiées.

## Validation 0.6.0 — 2026-11-03

- Commande : `python tools/build.py --task Package --organisation-profile config/organisations/demo/organisation.json`, avec JDK 21.
- Maven clean verify : 63 tests Java, 0 échec, 0 erreur, 0 ignoré.
- Python : 28 tests réussis. TypeScript : compilation et lint réussis ; 62 tests Node réussis.
- Interface Chromium : 12 scénarios réussis.
- Démarrage des JAR : version du moteur, santé de la gateway et refus des requêtes anonymes vérifiés.
- DEVAI-188 : signature Ed25519 réelle, priorité du mapping signé, ajout de projet avec la même instance après expiration du cache, absence de droit IA, repli properties, absence de mapping, clés invalides, expiration, signature invalide et propagation du refus anti-rejeu vérifiés. Les parcours Sonar local et lecture utilisent le résolveur partagé.
- Services métier simulés pour cette validation ; aucun appel réel Mistral. Recette sur l’autre PC non exécutée.
- Packaging : VSIX 0.6.0 inspecté ; runtime gateway et contrat Vibe embarqué réussis (HTTP simulé, six cas CLI). Livraison suiteInstall et archive 0.6.0 générées ; démarrage des JAR copiés réussi. Le profil demo contient des placeholders et doit être remplacé par le profil de l’organisation pour un usage réel.
