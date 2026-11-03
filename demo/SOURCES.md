# Sources et périmètre des affirmations

État du dépôt étudié : commit `385ea41b61d77a01ecb9a58f98deb59d356d77ef`. Les notes du PowerPoint contiennent également des liens GitHub figés sur ce commit. Le support de présentation a sa propre édition 1.0, sans changer la version du produit.

## Sources principales

| Sujet | Source locale | Usage dans le support |
|---|---|---|
| Interface et cinq onglets | [presentation.ts](../vscode-extension/src/presentation.ts) | Intitulés, modes et commandes visibles |
| Parcours automatiques et limites | [view.ts](../vscode-extension/src/view.ts) | Branches, tentatives, enchaînement des validations, limites de rapport |
| Rapport et couverture | [correction.ts](../vscode-extension/src/correction.ts) | États, garde-fous et présentation du résultat |
| Starters | [StarterService.java](../ods-java-core/src/main/java/org/organisation/ods/starter/StarterService.java), [StarterOverrides.java](../ods-java-core/src/main/java/org/organisation/ods/starter/StarterOverrides.java) | Stacks, sources et surcharges signées |
| Outils de workspace | [WorkspaceTools.java](../ods-java-core/src/main/java/org/organisation/ods/runtime/WorkspaceTools.java) | Contexte, accès et outils locaux |
| Scans locaux | [LocalAnalysisService.java](../ods-gateway/src/main/java/org/organisation/ods/integration/LocalAnalysisService.java) | Lancement, provenance et résultats des analyses |
| Gouvernance | [SignedDocuments.java](../ods-java-core/src/main/java/org/organisation/ods/security/SignedDocuments.java), [PolicyService.java](../ods-gateway/src/main/java/org/organisation/ods/policy/PolicyService.java) | Signature, dates, cache et règles |
| Architecture | [ARCHITECTURE.md](../docs/ARCHITECTURE.md), [EVOLUTION-0.3.0.md](../docs/EVOLUTION-0.3.0.md) | Frontières de confiance, runtime et secrets embarqués |
| Configuration et prérequis | [CONFIGURATION.md](../docs/CONFIGURATION.md), [INSTALLATION.md](../docs/INSTALLATION.md), [PROFILS-ORGANISATION.md](../docs/PROFILS-ORGANISATION.md) | Installation, Mistral/Vibe et configuration organisationnelle |
| Évolutions fonctionnelles | [CHANGELOG.md](../CHANGELOG.md) | Corrections 0.5.0, MR 0.4.0, mapping dynamique 0.6.0 |
| Mécanique | [README.md](../mecanique/README.md), [SPECIFICATION.md](../mecanique/SPECIFICATION.md) | Checkout de deux fichiers, édition interactive et publication |
| Droits de diffusion | [package.json](../vscode-extension/package.json) | Déclaration `UNLICENSED`, sans conclusion juridique |

## Preuves historiques utilisées

| Preuve | Date / version | Ce qui est établi dans le compte rendu | Limite conservée dans le support |
|---|---|---|---|
| [Validation du produit](../docs/VALIDATION.md) | 03/11/2026, 0.6.0 | 63 tests Java, 28 Python, 62 Node, 12 Chromium, packaging et démarrage des JAR | Services métier simulés et aucun appel réel Mistral dans cette validation |
| [Parcours Test1](../TestDOKERMan/VALIDATION.md) | 31/10/2026, 0.5.0 | Quality corrigée par Mistral, couverture 30,8 % à 100 % pour cible 80 %, 15 tests réussis | Petit projet Java, résultat historique après correction d’un défaut UTF-8 |
| [Security Test1](../TestDOKERMan/VALIDATION.md) | 31/10/2026, 0.5.0 | Dépendance mise à jour de 3.17.0 à 3.18.0, deux entrées synthétiques disparues | Mock Xray, ni vraie CVE ni qualification JFrog réelle |
| [Validation Mécanique](../mecanique/README.md#validation-de-cette-livraison) | 03/11/2026, 1.0.0 | 21 tests, Git bare local réel et signatures Ed25519 réelles | Aucun GitLab organisationnel, Docker ou Mistral utilisé |
| [Starters historiques](../docs/VALIDATION.md) | Socle 0.1.0, historique conservé | Projets Java, Angular, Python et .NET générés et validés | Ne garantit pas toutes les versions ni la disponibilité future des registres |

Ces preuves n’ont pas été réexécutées pour produire la présentation. Aucun résultat de CI supplémentaire, déploiement en production, audit de sécurité, certification ou retour sur investissement n’est revendiqué.

## Documents historiques à lire avec leur date

La [matrice de conformité](../docs/CONFORMITE.md) se présente comme un état 0.3.0. Elle recense des écarts de réalisation et de qualification. Elle ne doit pas annuler les fonctions documentées dans les évolutions 0.4.0 à 0.6.0, ni servir à annoncer une conformité exhaustive. Pour le comportement courant, le support privilégie le code inspecté et le changelog récent.

Certaines validations anciennes indiquent l’absence de services réels. La recette Test1 ultérieure apporte des preuves locales avec Mistral réel et des services Docker, sans qualifier les instances de l’organisation. Le support conserve cette distinction.

## Origine de la capture

[interface-ia.png](assets/interface-ia.png) est la copie inchangée d’un rendu existant de la webview 0.6.0. Le projet et la consommation affichés sont des données de démonstration. La capture ne montre ni credential, ni chemin personnel. Les diapositives et notes indiquent ce caractère illustratif.

## Propositions et hypothèses

Le calendrier de quatre semaines, les deux dépôts, les cinq à dix volontaires, les responsabilités et les indicateurs sont des propositions de pilote. Les bénéfices sont des hypothèses à mesurer. Les tarifs et coûts sont à renseigner à partir des conditions réelles de l’organisation. Aucun benchmark concurrentiel n’est fourni.

Les évolutions proposées, notamment le découpage des gros rapports et un autre modèle de gestion des credentials, ne sont pas présentées comme livrées.

## Confidentialité

Le dossier ne reproduit aucune clé API, mot de passe, adresse personnelle ou configuration privée du banc. Il ne reprend pas le PDF historique contenant les valeurs réelles. Les liens sources renvoient au dépôt demandé, sans inclure de secret dans leur URL. Cette vérification concerne les nouveaux fichiers de `demo`, pas un audit intégral de l’historique existant du dépôt.
