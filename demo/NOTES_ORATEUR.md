# Notes de présentation ODS

Édition 1.0, 2026-11-03. 24 diapositives principales et 6 annexes. Les mêmes notes figurent dans le PowerPoint. Les sources renvoient à l’état du dépôt étudié, sans relancer les validations historiques.

## 1. ODS L’assistance IA dans notre chaîne de qualité

Notre proposition est simple : rapprocher l’assistance IA des règles de notre organisation et des outils qui vérifient déjà notre code. ODS rassemble ces parcours dans VS Code, depuis la création d’un projet jusqu’à la préparation d’une merge request. La décision proposée aujourd’hui est de conduire un pilote mesuré sur des projets autorisés. Nous allons examiner les usages, les mécanismes de contrôle, les résultats déjà observés et les conditions d’un déploiement responsable.

Sources : [README.md](../README.md), [CHANGELOG.md](../CHANGELOG.md), [README.md](../mecanique/README.md)

## 2. Le travail qui entoure un correctif

Nous disposons déjà de GitLab, de SonarQube et d’outils de sécurité. Le coût opérationnel se situe souvent entre ces outils : retrouver le bon fichier, interpréter le rapport, produire un correctif, vérifier les tests et raconter le changement. ODS automatise une partie de ce travail de liaison. Nous devons mesurer le temps réellement économisé après avoir compté la relecture et les corrections humaines. Cette présentation ne s’appuie sur aucune étude comparative ni estimation de gain déjà acquise.

Sources : [view.ts](../vscode-extension/src/view.ts), [presentation.ts](../vscode-extension/src/presentation.ts)

## 3. Le parcours proposé dans VS Code

Le cas d’usage le plus démonstratif commence par un rapport de qualité ou de sécurité. Le bouton de correction prépare lui-même la consigne de l’agent. Il n’est pas nécessaire de recopier le rapport dans le chat. Le moteur crée une branche, applique les changements, vérifie le projet puis relance l’agent si nécessaire, dans la limite choisie. L’extension présente ensuite les résultats et le diff. Le commit, le push et la revue restent des actions distinctes. Un arrêt au plafond de tentatives peut laisser un correctif partiel clairement signalé.

Sources : [view.ts](../vscode-extension/src/view.ts), [correction.ts](../vscode-extension/src/correction.ts)

## 4. Une valeur différente pour chaque métier

Le développeur bénéficie d’un parcours plus continu. Le reviewer reçoit une description structurée et des questions précises, mais conserve la décision. L’équipe plateforme peut distribuer une configuration commune et administrer les droits. La sécurité doit qualifier les transferts et la protection des credentials. Le management peut décider à partir de résultats comparables. Ce sont des bénéfices attendus, pas des engagements de performance. Chaque métier a donc un indicateur et un rôle dans l’évaluation.

Sources : [presentation.ts](../vscode-extension/src/presentation.ts), [CONFIGURATION.md](../docs/CONFIGURATION.md), [CHANGELOG.md](../CHANGELOG.md)

## 5. Cinq onglets pour le cycle de développement

Cette vue donne le périmètre fonctionnel du produit. Toutes ces entrées sont présentes dans l’interface, mais leur disponibilité effective dépend des droits et de la configuration du projet. Les fonctions de génération IA nécessitent une politique valide et le service Mistral. Les opérations de scan et les actions Git suivent leurs propres contrôles. La présence d’une fonction ne vaut pas qualification de toutes les stacks et de tous les environnements d’entreprise.

Sources : [presentation.ts](../vscode-extension/src/presentation.ts), [CONFORMITE.md](../docs/CONFORMITE.md)

## 6. Starter : une base de projet commune

Starter fournit une base cohérente pour démarrer. L’organisation peut fournir un remplacement complet ou un overlay, avec contrôle de signature et d’empreinte. Un fichier de provenance permet d’identifier l’origine du starter. Les générateurs ne constituent pas un service universel de mise à niveau des projets existants. Les paramètres proposés restent simples. La documentation historique rapporte des builds des quatre starters, mais le pilote doit vérifier les versions, registres et standards réellement utilisés par notre organisation.

Sources : [StarterService.java](../ods-java-core/src/main/java/org/organisation/ods/starter/StarterService.java), [StarterOverrides.java](../ods-java-core/src/main/java/org/organisation/ods/starter/StarterOverrides.java), [VALIDATION.md](../docs/VALIDATION.md)

## 7. IA : le contexte du projet dans la conversation

L’interface présente trois modes. Le contexte vient du workspace et des fichiers sélectionnés, avec des restrictions de chemins et un filtrage des secrets. Les instructions de l’organisation peuvent préciser les règles globales, les pratiques de stack et les tâches. Les outils passent par le moteur, les autorisations et les contrôles de provenance. Les checkpoints permettent de revenir sur les changements du parcours prévu. Cette image est une capture de la webview avec des données de test, pas un échange Mistral réel ni une mesure de consommation.

Sources : [presentation.ts](../vscode-extension/src/presentation.ts), [view.ts](../vscode-extension/src/view.ts), [WorkspaceTools.java](../ods-java-core/src/main/java/org/organisation/ods/runtime/WorkspaceTools.java)

## 8. Quality : un diagnostic lié au code analysé

Le rapport n’est pas une estimation du modèle. Il provient du scanner et de SonarQube. Les identifiants et l’empreinte du workspace relient l’analyse à l’état du projet. Le parcours Java exécute notamment la vérification Maven avant l’analyse Sonar. Les autres stacks exigent leurs outils et rapports de couverture. Une métrique absente ne signifie pas zéro pour cent : il faut d’abord configurer la mesure. Un clic sur la correction ne supprime pas la nécessité de comprendre l’anomalie et de relire le patch.

Sources : [LocalAnalysisService.java](../ods-gateway/src/main/java/org/organisation/ods/integration/LocalAnalysisService.java), [view.ts](../vscode-extension/src/view.ts)

## 9. Security : relier dépendances et corrections

ODS prend en charge la construction d’une SBOM locale pour les stacks prévues et normalise les réponses du service Xray. Le correctif doit préserver le fonctionnement du projet, pas seulement modifier un numéro de version. Le banc disponible simule le contrat Graph Scan et des vulnérabilités synthétiques. Il a démontré le parcours de bout en bout avec Mistral, mais ne démontre ni la détection réelle de JFrog ni la compatibilité de toutes ses configurations. La sécurité conserve la décision sur les versions acceptables et les risques résiduels.

Sources : [presentation.ts](../vscode-extension/src/presentation.ts), [LocalAnalysisService.java](../ods-gateway/src/main/java/org/organisation/ods/integration/LocalAnalysisService.java), [VALIDATION.md](../TestDOKERMan/VALIDATION.md)

## 10. La boucle de correction automatique

Le bouton de correction donne l’autorisation de produire et d’appliquer le correctif dans ce parcours. Il n’exige pas une consigne complémentaire dans le chat. Si le build ou les tests échouent, l’agent reçoit les erreurs avec le rapport initial. Si l’objectif reste incomplet après un scan valide, il reçoit les nouveaux constats. La boucle s’arrête au succès, à l’annulation, à un refus ou au plafond configuré. Elle ne garantit pas de converger et ne publie pas automatiquement la branche. Les autorisations de commandes et les quotas restent applicables.

Sources : [view.ts](../vscode-extension/src/view.ts), [correction.ts](../vscode-extension/src/correction.ts), [CHANGELOG.md](../CHANGELOG.md)

## 11. Augmenter la couverture avec un objectif explicite

Le parcours vise à compléter les tests à partir d’une mesure réelle. Il ne doit pas améliorer artificiellement le score en excluant du code. Des contrôles surveillent les exclusions et le périmètre mesuré. Le reviewer doit néanmoins examiner la qualité des assertions, les scénarios métier et les cas limites. Si Sonar ne fournit pas de couverture, la bonne action est de configurer le rapport de couverture. Le seuil se fixe selon le contexte du projet. Cent pour cent n’est pas une cible universelle proposée par cette présentation.

Sources : [correction.ts](../vscode-extension/src/correction.ts), [view.ts](../vscode-extension/src/view.ts)

## 12. Un rapport rattaché aux constats initiaux

Le rapport part des anomalies de l’analyse initiale. Chaque entrée conserve son contexte et reçoit un état vérifiable. La description du modèle accompagne les preuves techniques mais ne les remplace pas. Une réponse qui raconte une correction sans fichier appliqué reste non implémentée. Le rapport est lisible dans l’extension et exportable en PDF avec masquage de chemins privés et de secrets reconnus. Pour conserver la preuve de la session, il faut exporter le PDF avant de recharger la vue. Aucun archivage central immuable du PDF n’est revendiqué.

Sources : [correction.ts](../vscode-extension/src/correction.ts), [presentation.ts](../vscode-extension/src/presentation.ts), [view.ts](../vscode-extension/src/view.ts)

## 13. GitFlow : préparer une revue utile

La pré-revue vise à faire gagner du temps au reviewer en lui donnant des points d’attention. Elle ne prononce pas une approbation du code. La création utilise le diff publié sur GitLab et vérifie que les branches n’ont pas changé depuis la préparation. Une MR déjà ouverte n’est pas écrasée. Les contrôles refusent les diffs tronqués ou trop volumineux. Les vérifications proposées par le modèle ne sont pas annoncées comme des tests exécutés. Les fonctions changelog et release notes produisent des documents contrôlés, sans créer automatiquement une release.

Sources : [CHANGELOG.md](../CHANGELOG.md), [view.ts](../vscode-extension/src/view.ts), [presentation.ts](../vscode-extension/src/presentation.ts)

## 14. Architecture et circulation des données

L’extension démarre sa gateway locale et gère son cycle de vie. Le moteur Java porte les accès au workspace et l’exécution contrôlée des actions. Les appels Mistral utilisent le transport configuré, notamment le bridge Vibe embarqué. Le code sélectionné peut quitter le poste vers le fournisseur IA. SonarQube et Xray reçoivent également les éléments nécessaires à leur analyse. Le périmètre exact, les destinations, la conservation et les clauses de service doivent être examinés avec la sécurité de l’organisation. L’architecture n’est pas une garantie d’absence de fuite.

Sources : [ARCHITECTURE.md](../docs/ARCHITECTURE.md), [CONFIGURATION.md](../docs/CONFIGURATION.md), [EVOLUTION-0.3.0.md](../docs/EVOLUTION-0.3.0.md)

## 15. La gouvernance conditionne les actions IA

Les droits ne se résument pas à la possession d’une clé API. L’accès au dépôt, la politique signée et le droit pour l’action demandée interviennent dans la décision. Les quotas limitent les consommations, mais une tentative peut comporter plusieurs appels. Le filtrage de contexte et les contrôles des outils réduisent certains risques sans constituer une preuve complète de résistance à toutes les injections ou fuites. Une politique manquante ou expirée n’est pas contournée. La clé privée de signature appartient à l’administration, pas au développeur utilisant l’extension.

Sources : [SignedDocuments.java](../ods-java-core/src/main/java/org/organisation/ods/security/SignedDocuments.java), [PolicyService.java](../ods-gateway/src/main/java/org/organisation/ods/policy/PolicyService.java), [CONFIGURATION.md](../docs/CONFIGURATION.md)

## 16. Mécanique simplifie l’ajout des projets

Mécanique est un outil séparé destiné à l’administrateur. Il laisse les autres fichiers du dépôt de gouvernance hors du checkout et hors du commit. Les métadonnées Git peuvent néanmoins transporter d’autres objets si le serveur ne prend pas en charge le filtre de fetch. L’outil vérifie la signature existante et les conflits de publication. Il ne crée ni le projet GitLab ni le projet SonarQube. Il n’installe pas de tâche de renouvellement périodique. L’ajout d’un modèle au catalogue de gouvernance ne reconfigure pas à lui seul le fournisseur embarqué dans le VSIX.

Sources : [README.md](../mecanique/README.md), [SPECIFICATION.md](../mecanique/SPECIFICATION.md), [CHANGELOG.md](../CHANGELOG.md)

## 17. Les conditions d’installation et d’exploitation

Le VSIX contient une configuration organisationnelle préparée au packaging. Certaines valeurs, notamment les URL, les credentials techniques et le modèle embarqué, restent liées à cette configuration. La nouveauté 0.6.0 déplace le mapping Sonar des projets dans la politique, mais ne rend pas toute la configuration dynamique. Python 3.12 ne suffit pas à lui seul : le runtime Vibe, le modèle, les droits API, le réseau et les certificats doivent fonctionner ensemble. Le banc Docker est utile pour démontrer les interactions, pas pour présumer les conditions de production.

Sources : [INSTALLATION.md](../docs/INSTALLATION.md), [CONFIGURATION.md](../docs/CONFIGURATION.md), [PROFILS-ORGANISATION.md](../docs/PROFILS-ORGANISATION.md), [README.md](../TestDOKERMan/README.md)

## 18. Un modèle économique à mesurer

Nous proposons de mesurer les coûts réels plutôt que de choisir un taux de productivité arbitraire. Le temps net économisé doit tenir compte du diagnostic, de la génération, des attentes, de la revue et des reprises. Les services peuvent déjà exister, mais leur coût incrémental et leur administration ne sont pas nécessairement nuls. Les tentatives, les quotas et le périmètre de contexte servent à maîtriser la consommation. Les droits de redistribution et licences des composants doivent être vérifiés avant une diffusion organisationnelle. Ce document ne constitue pas un devis.

Sources : [view.ts](../vscode-extension/src/view.ts), [CONFIGURATION.md](../docs/CONFIGURATION.md)

## 19. Une preuve locale sur un petit projet Java

Ce graphique reprend une preuve documentée, pas une nouvelle exécution réalisée pour cette présentation. Le scénario partait de 30,8 pour cent de couverture et visait 80 pour cent. Après correction d’un défaut d’encodage dans le produit, le parcours final a atteint 100 pour cent avec quinze tests et une tentative. Mistral a modifié CalculatorTest.java. Le même banc rapporte une correction Quality de java:S106, puis une mise à jour de dépendance pour deux entrées synthétiques du mock Xray. Ces résultats montrent la faisabilité du parcours sur un petit exemple. Ils ne prouvent ni un gain de productivité ni une capacité équivalente sur un grand dépôt.

Sources : [VALIDATION.md](../TestDOKERMan/VALIDATION.md)

## 20. Des preuves distinctes selon le périmètre

Il est utile de distinguer les tests du code, les parcours d’interface avec services locaux et la qualification d’entreprise. La validation 0.6.0 utilise des services métier simulés et ne fait aucun appel Mistral réel. La recette Test1 précédente utilise réellement Mistral, SonarQube et GitLab local, mais un Xray simulé. Mécanique vérifie ses signatures et ses publications dans des dépôts Git locaux. La matrice de conformité existante conserve des écarts de réalisation ou de qualification. Nous ne revendiquons pas une certification, une couverture exhaustive des exigences ni le succès d’une CI qui n’aurait pas été vérifiée.

Sources : [VALIDATION.md](../docs/VALIDATION.md), [VALIDATION.md](../TestDOKERMan/VALIDATION.md), [CHANGELOG.md](../mecanique/CHANGELOG.md), [README.md](../mecanique/README.md), [CONFORMITE.md](../docs/CONFORMITE.md)

## 21. Les points à traiter avant une généralisation

Le risque le plus important à expliciter est la distribution des secrets techniques dans un package autonome : leur masquage dans l’interface ne les rend pas inaccessibles à l’utilisateur du poste. La politique signée n’est pas un coffre de secrets. L’organisation doit accepter ce modèle ou retenir une évolution avant le pilote. Les gros rapports se heurtent également à des limites de contexte. Les problèmes de connexion et de runtime exigent des diagnostics maîtrisés. La généralisation dépend de ces décisions et de la recette, pas uniquement d’une démonstration réussie.

Sources : [EVOLUTION-0.3.0.md](../docs/EVOLUTION-0.3.0.md), [CONFORMITE.md](../docs/CONFORMITE.md), [view.ts](../vscode-extension/src/view.ts), [CONFIGURATION.md](../docs/CONFIGURATION.md), [package.json](../vscode-extension/package.json)

## 22. Un pilote proposé sur quatre semaines

Le pilote n’est pas un engagement de calendrier déjà approuvé. Nous proposons deux dépôts, cinq à dix volontaires et quatre semaines pour obtenir un échantillon utile sans distribuer immédiatement l’outil à toute l’organisation. La première semaine inclut les décisions de sécurité, les droits et la mesure de référence. La sécurité ne doit être testée contre le vrai Xray qu’après configuration et qualification. Si les conditions ne sont pas réunies, ce parcours reste explicitement hors du pilote. Le tech lead choisit des anomalies représentatives et évite de sélectionner uniquement les cas faciles.

Sources : Proposition pour le pilote.

## 23. Les critères de décision du pilote

Une mesure utile prend en compte les cas qui échouent, le support et les reprises manuelles. Nous proposons de comparer des tâches de nature et de complexité proches plutôt que des volumes de code généré. Les seuils de réussite sont à décider avant le démarrage. Le temps de revue et les régressions sont aussi importants que la vitesse du premier patch. Les tokens sont une mesure de consommation, pas une preuve de valeur. Le bilan peut conduire à élargir, corriger puis prolonger le pilote, ou arrêter si le rapport coût-bénéfice ne convient pas.

Sources : Proposition pour le pilote.

## 24. La décision proposée

Notre demande est concrète : choisir un sponsor, deux projets et les personnes qui porteront la recette. ODS propose déjà une chaîne cohérente entre diagnostic, correction vérifiée et revue. Les preuves locales justifient un essai limité, avec des points de sécurité et de fiabilité clairement ouverts. L’objectif du pilote est de déterminer où cette solution nous aide vraiment et à quel coût. La décision de généralisation viendra après les résultats, avec les équipes de développement, de plateforme et de sécurité.

Sources : Proposition pour le pilote.

## 25. Annexe · Démonstration en réunion

Le guide DEMONSTRATION.md décrit les préconditions, les actions et le résultat attendu. Préparer chaque scénario sur un clone ou une branche indépendante évite qu’une correction précédente ne supprime l’anomalie du scénario suivant. Un nombre limité de tentatives et un seul scénario de génération principal suffisent pour la réunion. Ne pas présenter une vidéo historique comme une exécution en direct. En cas d’échec, montrer le diagnostic, conserver la preuve et revenir aux résultats documentés, en annonçant clairement qu’ils proviennent du banc précédent.

Sources : [README.md](../TestDOKERMan/README.md), [VALIDATION.md](../TestDOKERMan/VALIDATION.md), [view.ts](../vscode-extension/src/view.ts)

## 26. Annexe · Réponses aux objections

Répondre directement aux objections aide à obtenir une décision crédible. Ne pas dénigrer les assistants déjà utilisés : la différence proposée est l’intégration du workflow et de la gouvernance dans notre environnement. Aucune comparaison indépendante avec d’autres produits n’a été effectuée. La sécurité, la qualité des tests et l’exploitation sont des sujets de validation, pas des obstacles à minimiser. Le dossier d’accompagnement donne des réponses plus détaillées et les limites exactes.

Sources : [ARCHITECTURE.md](../docs/ARCHITECTURE.md), [CONFORMITE.md](../docs/CONFORMITE.md), [EVOLUTION-0.3.0.md](../docs/EVOLUTION-0.3.0.md)

## 27. Annexe · Ce qui change sans nouveau VSIX

Cette distinction évite de promettre une administration entièrement dynamique. Le mapping Sonar peut maintenant évoluer par politique, sans reconstruire l’extension déjà en 0.6.0. La politique conserve les contrôles de signature, d’expiration et d’anti-rejeu. Le repli vers les properties existe pour compatibilité et ne sert pas à contourner une politique invalide. Le cache courant est de soixante secondes. Modifier le catalogue de modèles ne suffit pas pour choisir un autre backend ou rendre un modèle disponible chez Mistral. Les URL, secrets techniques et paramètres embarqués relèvent encore du profil de livraison.

Sources : [CHANGELOG.md](../CHANGELOG.md), [CONFIGURATION.md](../docs/CONFIGURATION.md), [PROFILS-ORGANISATION.md](../docs/PROFILS-ORGANISATION.md), [README.md](../mecanique/README.md)

## 28. Annexe · Limites techniques à connaître

La limite de correction porte sur la longueur du JSON evidence construit par l’extension, mesurée par la longueur de chaîne JavaScript. Elle ne correspond pas à la taille générale de la fenêtre de contexte Mistral. Le blocage survient avant l’appel au modèle : modifier le prompt ne permet donc pas de découper automatiquement ce rapport. La pré-revue applique ses propres limites. Les erreurs Vibe doivent être diagnostiquées avec les traces techniques expurgées. Une clé fonctionnant dans un autre plugin ne prouve pas que le runtime, le modèle et la configuration réseau d’ODS sont identiques.

Sources : [view.ts](../vscode-extension/src/view.ts), [CHANGELOG.md](../CHANGELOG.md), [CONFIGURATION.md](../docs/CONFIGURATION.md)

## 29. Annexe · Les évolutions à prioriser

Le pilote doit produire une liste de travaux ordonnée par impact, pas uniquement une demande de nouvelles fonctions. Une évolution de la gestion des secrets ou de l’hébergement de la gateway peut devenir prioritaire selon la politique de l’organisation. Le découpage des rapports exige des changements de produit, des contrôles de cohérence et des tests. La qualification multi-stack doit porter sur les projets réels. Le renouvellement automatique de la politique nécessite une exploitation dédiée : Mécanique est actuellement un assistant interactif, pas un planificateur.

Sources : [CONFORMITE.md](../docs/CONFORMITE.md), [view.ts](../vscode-extension/src/view.ts), [README.md](../mecanique/README.md)

## 30. Annexe · Le message à retenir

Pour conclure après les questions, revenir à la décision de pilote. L’intérêt de la solution tient au lien entre les outils et à la preuve qui accompagne le changement. Nous disposons de fonctions concrètes et de preuves locales, ainsi que d’une liste explicite de limites. Cette transparence permet de choisir un périmètre d’essai utile. La prochaine étape est de désigner les projets et responsables, puis de définir les critères d’acceptation avant la première utilisation sur des données de l’organisation.

Sources : [view.ts](../vscode-extension/src/view.ts), [VALIDATION.md](../TestDOKERMan/VALIDATION.md)
