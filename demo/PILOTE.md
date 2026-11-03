# Proposition de pilote ODS

Statut : proposition à discuter, sans décision ni budget déjà approuvés.

## Périmètre initial

- Quatre semaines, deux dépôts autorisés, cinq à dix volontaires.
- Démarrage Java pour utiliser la stack du parcours réel documenté.
- Anomalies Quality ciblées, revue de MR et couverture sur des composants choisis.
- Security avec JFrog réel seulement après qualification de l’intégration. Le mock reste un outil de démonstration.
- Petites MR et rapports compatibles avec les limites actuelles.

## Responsabilités

| Responsable à désigner | Mission |
|---|---|
| Sponsor | Valider le périmètre, les ressources et la décision de poursuite |
| Tech lead | Choisir les cas, relire les correctifs et définir les critères de qualité |
| Plateforme | Distribuer le profil, administrer les services et résoudre les incidents |
| Sécurité | Approuver les données, les destinations et le modèle de gestion des secrets |
| Administrateur de gouvernance | Maintenir la politique, protéger les clés et renouveler la validité |
| Développeurs volontaires | Exécuter les parcours, consigner les résultats et les reprises |
| Reviewers | Évaluer l’utilité de la pré-revue et la qualité des changements |

## Conditions d’entrée

Une décision doit couvrir les secrets techniques extractibles du VSIX. Si l’organisation refuse ce modèle, une évolution est nécessaire avant la distribution. Il faut aussi définir les données autorisées à sortir, qualifier les accès, prévoir le renouvellement de la politique et choisir un responsable de support. Le pilote utilise des projets pour lesquels l’utilisation de Mistral est autorisée. Le manifeste de l’extension déclare `UNLICENSED` : clarifier les droits d’utilisation et de diffusion avec le propriétaire, ainsi que les obligations des dépendances. Ce constat ne constitue pas un avis juridique.

## Calendrier proposé

| Semaine | Activités | Livrable |
|---|---|---|
| 1 | Décisions sécurité, accès, installation, cas représentatifs et mesure initiale | Fiche de périmètre et critères fixés |
| 2 | Quality et pré-revue MR, accompagnement des volontaires | Journal des parcours et retours reviewers |
| 3 | Couverture, éventuellement Security réel, analyse des échecs | Bilan intermédiaire et corrections prioritaires |
| 4 | Comparaison, coût complet, qualité et adoption | Décision argumentée de poursuite ou d’arrêt |

## Fiche de mesure à remplir par tâche

| Champ | Définition |
|---|---|
| Identifiant de tâche | Référence interne non sensible |
| Type et complexité | Anomalie, test, dépendance, revue et niveau défini par l’équipe |
| Méthode | Parcours habituel ou ODS |
| Temps de diagnostic | Minutes effectivement consacrées à comprendre le problème |
| Temps de correction et attente | Temps actif et attente séparés |
| Temps de revue et reprise | Inclure les retours et corrections manuelles |
| Résultat | Accepté, partiel, refusé ou abandonné |
| Vérification | Build, tests, analyse, revue et défauts résiduels |
| Consommation | Tokens et coût réel applicable au compte |
| Incident | Erreur, indisponibilité ou besoin de support |
| Retour reviewer | Utilité du résumé et des recommandations |

Ne pas recopier de code, tokens ou rapports sensibles dans une feuille partagée sans autorisation. Conserver les tâches échouées dans l’échantillon. Éviter de comparer une correction triviale avec une évolution complexe. Si les effectifs sont faibles, présenter les résultats comme exploratoires.

## Calcul économique

Pour un groupe de tâches comparables :

`Temps net économisé = temps total habituel − temps total ODS, revue et reprises incluses`

`Valeur du temps = heures nettes économisées × coût horaire interne convenu`

`Coût complet = usage Mistral + coûts incrémentaux des services + administration + accompagnement`

`Valeur nette = valeur du temps − coût complet`

Utiliser des coûts réellement constatés ou des hypothèses explicitement approuvées. Aucun montant n’est prérempli. Éviter de valoriser deux fois le temps de revue ou le support. Les coûts existants et les coûts incrémentaux doivent être distingués.

## Critères de sortie

Les responsables fixent avant le démarrage les seuils attendus pour le temps net, les régressions, le coût et la fiabilité. La décision est l’une des suivantes : élargir progressivement, corriger puis prolonger, ou arrêter. Un incident de données ou une condition de sécurité non satisfaite peut imposer un arrêt indépendant du gain de temps.

## Points ouverts

La généralisation exige notamment la qualification JFrog réelle, des décisions sur la distribution des secrets, une exploitation du renouvellement de politique, des diagnostics utilisables et une couverture des stacks cibles. Le découpage automatique des gros rapports et une gestion différente des credentials sont des évolutions proposées, pas des fonctions livrées.
