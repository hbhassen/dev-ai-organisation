# Pitch ODS pour une réunion d’adoption

Édition 1.0, 3 novembre 2026. Support fondé sur ODS 0.6.0 et Mécanique 1.0.0. Les résultats cités sont ceux des comptes rendus du dépôt. Ils ne constituent pas une nouvelle recette du produit.

## Pitch de 90 secondes

Nous avons déjà des outils qui détectent les problèmes dans le code. Il reste ensuite à interpréter leurs rapports, retrouver le contexte, préparer un correctif, lancer les tests et expliquer le changement au reviewer.

ODS rassemble ce parcours dans VS Code. Le développeur peut générer un projet standard, interroger Mistral avec le contexte autorisé, analyser la qualité et les dépendances, puis demander une correction. L’agent applique le patch dans une branche dédiée. L’extension lance la compilation, les tests et un nouveau scan. En cas d’échec, elle transmet les erreurs à l’agent et relance dans la limite choisie par l’utilisateur.

Le résultat reste vérifiable : diff, rapport par anomalie, export PDF et pré-revue de la merge request. L’organisation définit les projets autorisés, les actions et les modèles au moyen d’une politique signée. L’outil Mécanique aide l’administrateur à maintenir cette politique sans toucher aux autres fichiers du dépôt de gouvernance.

Un banc local a déjà démontré des corrections avec Mistral réel, SonarQube et GitLab sur Docker. Xray y reste simulé. Nous avons aussi des limites à traiter, notamment la distribution des secrets, les gros rapports et la qualification de nos services.

Je propose donc un pilote de quatre semaines sur deux projets, avec des volontaires, des responsabilités claires et des mesures de temps, de qualité et de coût. Nous déciderons de l’adoption à partir de nos propres résultats.

## Pitch de 8 à 10 minutes

### 1. Le problème et l’opportunité — 1 minute

Notre chaîne de développement contient déjà des contrôles. Sonar identifie des anomalies, les outils de sécurité analysent les dépendances et GitLab organise la revue. Cependant, le développeur assure encore manuellement le lien entre le constat, le correctif et la preuve de vérification. Il doit aussi donner au reviewer le contexte nécessaire.

ODS propose de regrouper ce travail dans l’IDE. La valeur attendue tient à la réduction des manipulations et à une meilleure continuité de l’information. Il faudra la mesurer, car une génération rapide peut être suivie d’une relecture longue.

### 2. Le périmètre fonctionnel — 1 minute

Cinq onglets couvrent le parcours. Starter génère les bases Java, Angular, Python ou .NET, avec des surcharges organisationnelles. IA propose les modes Question, Modification et Agent, avec des fichiers joints et des instructions communes. Quality utilise SonarQube pour les anomalies et la couverture. Security génère une SBOM CycloneDX et utilise le contrat de scan Xray. GitFlow permet de préparer et publier le travail dans GitLab, avec une pré-revue IA de la MR, un changelog et des release notes.

Les droits et prérequis déterminent les actions disponibles. La présence d’un onglet ne signifie pas que toutes les configurations d’entreprise ont déjà été qualifiées.

### 3. Le parcours de correction — 2 minutes

Prenons une anomalie Sonar. Le développeur analyse les fichiers enregistrés. Il sélectionne les constats et clique sur Corriger les issues. L’extension construit la consigne à partir du rapport : il n’a pas besoin de la recopier dans le chat.

Le moteur vérifie que l’état du projet correspond au rapport, crée une branche dédiée et applique les fichiers produits par Mistral avec des checkpoints. Il lance ensuite le build et les tests. Si ceux-ci échouent, l’agent reçoit les erreurs avec le contexte initial. Si le projet passe ces contrôles, un nouveau scan confirme ou infirme la résolution. Le processus recommence si nécessaire dans la limite de tentatives choisie.

Le résultat peut être complet, partiel ou non confirmé. Le rapport l’indique pour chaque constat initial. Le développeur peut ouvrir le diff, examiner les logs et télécharger le PDF. Il conserve la responsabilité de la revue, du commit et de la publication. La boucle ne promet pas de corriger n’importe quel projet.

Le parcours de couverture suit le même principe. L’utilisateur fixe une cible. L’agent ajoute des tests et la mesure Sonar permet de décider si l’objectif est atteint. Les contrôles surveillent les exclusions et le périmètre de couverture. La qualité des assertions reste un sujet de revue humaine.

### 4. La valeur pour la revue — 1 minute

Lors de la création d’une MR, la pré-revue conserve les commentaires du développeur et ajoute, pour chaque fichier modifié, un résumé, des recommandations au reviewer, des questions de clarification et des vérifications suggérées. Ces suggestions ne sont pas présentées comme des tests déjà exécutés.

L’extension s’appuie sur les branches publiées sur GitLab, contrôle la fraîcheur des commits et montre la description avant publication. Le reviewer peut ainsi concentrer son attention sur les choix techniques et les risques. Le pilote doit vérifier si cette aide réduit vraiment les allers-retours.

### 5. Le contrôle organisationnel — 1 minute

Les actions IA dépendent d’une politique signée. Elle relie les projets à des droits, modèles et quotas. La signature et les dates sont contrôlées. Une politique absente ou invalide provoque un refus.

Depuis la version 0.6.0, le mapping Sonar peut évoluer dans la politique, sans reconstruire le VSIX. Mécanique guide l’administrateur pour ajouter plusieurs projets, modifier leurs propriétés et renouveler la validité. Il checkoute seulement les deux fichiers de gouvernance, signe les octets du nouveau JSON et publie ces deux fichiers ensemble. Le reste du dépôt reste inchangé.

Certaines données de configuration restent embarquées. Le détenteur du VSIX peut extraire ses secrets techniques. Le modèle de distribution doit donc recevoir une décision de sécurité explicite. Le contexte filtré peut être transmis à Mistral : le produit n’est pas une solution hors ligne.

### 6. Les preuves et leurs limites — 1 minute

Le banc Test1 documente des corrections avec Mistral Medium 3.5 réel. Une anomalie Java a disparu au nouveau scan. Un scénario de couverture est passé de 30,8 % à 100 %, avec un objectif de 80 % et quinze tests réussis. Un scénario Security a mis à jour une dépendance puis supprimé deux constats synthétiques du mock Xray.

Ce sont de petits exemples. Ils prouvent la faisabilité du parcours, pas un rendement moyen ni la compatibilité avec notre Xray. La validation automatisée 0.6.0 rapporte 63 tests Java, 28 Python, 62 Node et 12 Chromium. Mécanique dispose de 21 tests. Ces validations complètent la démonstration, sans remplacer la recette des instances de l’organisation.

### 7. La décision — 1 minute

Je propose un pilote sur deux dépôts, avec cinq à dix volontaires, un responsable plateforme, un référent sécurité et un tech lead. Nous définirons les seuils de réussite avant de commencer. Nous suivrons le délai jusqu’au patch accepté, les reprises, les régressions, le coût complet et l’utilité perçue en revue.

À la fin, nous déciderons d’élargir, d’améliorer ou d’arrêter. Cette approche donne à l’organisation une décision fondée sur ses propres contraintes et ses propres résultats.

## Réponses développées aux objections

**« Nous utilisons déjà un assistant IA. »**

Le pilote doit comparer l’effort complet dans notre environnement. L’intérêt proposé par ODS est le lien explicite avec les rapports, la politique et la MR. Aucune étude comparative avec un autre assistant n’a été réalisée. Il ne faut pas annoncer une supériorité de génération ni demander un remplacement général avant mesure.

**« Le modèle va-t-il voir notre code ? »**

Il peut recevoir le contexte autorisé nécessaire à la tâche, après les filtres implémentés. Les services Sonar et Xray reçoivent aussi leurs données d’analyse. La classification des données, les conditions contractuelles et les destinations doivent être validées. Le masquage automatique n’est pas une garantie absolue.

**« Peut-on revenir en arrière ? »**

Les corrections automatiques utilisent une branche dédiée et des checkpoints. Les opérations de restauration prévues doivent être comprises et testées sur le projet pilote. Une branche et des checkpoints ne dispensent pas d’un historique Git sain ni de sauvegardes. Ne pas restaurer aveuglément un workspace contenant du travail supplémentaire.

**« La signature sécurise-t-elle la clé Mistral ? »**

La signature protège l’intégrité de la politique. Elle ne chiffre pas les secrets inclus dans le package. L’organisation doit décider de la distribution des credentials, de leur rotation et, si nécessaire, d’une évolution de l’architecture.

**« Peut-on ajouter un projet sans refaire la livraison ? »**

Oui pour les règles de projet et le mapping Sonar signé sur une extension 0.6.0, dans les services déjà configurés. Il faut néanmoins que les projets GitLab/Sonar existent et que les accès soient disponibles. Les URL, secrets techniques ou paramètres de runtime embarqués ne deviennent pas tous dynamiques.

**« Combien cela coûte-t-il ? »**

Le document ne fournit pas de tarif inventé. Il faut relever les coûts Mistral réellement applicables au compte, les licences et l’hébergement des services, puis ajouter l’administration et la revue. Le coût par correctif accepté est plus utile que le seul nombre de tokens. Le plafond de tentatives limite une boucle, sans représenter un plafond identique d’appels.

**« Et si le rapport est trop gros ? »**

La version inspectée refuse l’evidence de correction au-delà de 7 000 caractères. Le refus intervient avant le modèle. Demander au prompt de découper ne le contourne pas. Le pilote doit commencer avec des périmètres réduits. Un découpage automatique exige une évolution du produit.

**« Tout fonctionne-t-il sur notre réseau ? »**

Ce n’est pas encore établi. Les certificats, proxys, droits et versions des API doivent être qualifiés. Une clé API qui fonctionne dans un autre plugin ne valide pas le bridge Vibe et la configuration ODS. Les incidents doivent être consignés avec des traces expurgées.

**« Peut-on supprimer la revue humaine si les tests passent ? »**

Non. Les tests ne couvrent pas toutes les intentions métier et le scanner ne détecte pas tous les défauts. Le reviewer examine le sens du changement, la qualité des tests, les effets de bord et les risques résiduels.

## Adaptation à l’audience

- **Direction / responsables** : slides 1 à 4, 18 à 24. Décision, risques, coût et mesure.
- **Développeurs / tech leads** : slides 5 à 13, 19 et 25 à 28. Parcours concret et preuve.
- **Plateforme / sécurité** : slides 14 à 17, 20 à 23 et 27 à 29. Données, exploitation et qualification.
- **Présentation complète** : slides 1 à 24 en 25 à 35 minutes, puis annexes selon les questions. Ajouter le temps de démonstration séparément.
