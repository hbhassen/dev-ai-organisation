# Démonstration ODS en réunion

Ce guide prépare une démonstration à exécuter. Il ne décrit pas une nouvelle exécution effectuée pour la présentation. Les seules preuves historiques citées sont dans [le compte rendu Test1](../TestDOKERMan/VALIDATION.md).

## Objectif et durée

Montrer le lien entre un constat, un changement réellement généré par Mistral, sa vérification et sa transmission au reviewer. Prévoir 15 à 20 minutes, plus une réserve pour le démarrage des services et les scans. Ne pas annoncer une durée garantie.

Un parcours Quality réel suffit comme démonstration principale. Couverture, Security et MR peuvent être des séquences supplémentaires. Réduire les appels IA : un petit projet, un rapport ciblé, une limite de deux tentatives choisie avant la réunion. Deux tentatives peuvent contenir plusieurs appels.

## Préparation

1. Utiliser un profil VS Code de démonstration isolé avec le VSIX organisationnel configuré. Les profils `Test` ou `Test1` du banc sont historiques : vérifier leur contenu avant emploi.
2. Ouvrir un dépôt de démonstration, sans code privé ni secrets visibles. Préparer un état Git propre et un commit de référence. Utiliser des clones ou branches indépendants pour Quality, couverture et Security.
3. Vérifier Git, Java 21, Python 3.12 pour Vibe et les outils de la stack. Valider la connectivité vers les services, le certificat et les droits. Ne pas exposer les tokens dans la réunion.
4. Vérifier les dates et la signature de la politique. Le projet doit être autorisé pour les actions IA et le modèle configuré. Renouveler la politique avec l’outil administratif avant la séance si nécessaire.
5. Vérifier le mapping Sonar et la production du rapport de couverture. Pour la séquence couverture, choisir une cible supérieure à la mesure initiale.
6. Préparer une anomalie Sonar simple et connue. Pour le banc historique Java, `java:S106` était l’exemple utilisé. Confirmer qu’elle existe encore dans le scan du clone de démonstration.
7. Pour Security, annoncer explicitement si la destination est **mockXray**. Les entrées synthétiques du banc ne sont pas de vraies CVE. Une démonstration JFrog réelle exige sa propre configuration et qualification.
8. Pour la MR, utiliser un dépôt de test et des branches déjà publiées. La pré-revue porte sur le diff publié dans GitLab. Préparer des commits ne dépassant pas les limites de pré-revue.
9. Préparer un dossier d’export PDF et fermer les fenêtres susceptibles d’afficher des informations privées. Les captures présentes dans le PowerPoint illustrent l’interface avec des données de démonstration.

## Séquence principale : Quality

| Étape | Action dans l’interface | Résultat à montrer | Commentaire oral |
|---|---|---|---|
| 1 | Ouvrir Quality et cliquer sur **Analyser** | Rapport, constat et provenance | « Ce constat vient de Sonar. » |
| 2 | Choisir les anomalies et régler les tentatives | Périmètre de correction explicite | « Le budget de relances est limité. » |
| 3 | Cliquer sur **Corriger les issues** et valider la sélection | Branche dédiée, tâche de l’agent | « Le rapport construit la consigne. » |
| 4 | Laisser le parcours travailler sans clic concurrent | Application, build, tests et nouveau scan | « Le modèle produit le patch et les outils le vérifient. » |
| 5 | Examiner le rapport et le diff | Statut par anomalie, fichiers et preuve | « La conclusion dépend du résultat observé. » |
| 6 | Cliquer sur **Télécharger le PDF** | PDF lisible et expurgé | « Cette preuve peut accompagner la revue. » |

Ne pas saisir de consigne complémentaire pour faire réussir artificiellement ce scénario. Si l’agent n’applique rien ou rencontre un échec, conserver ce résultat et le commenter. Le diff peut présenter un résultat incomplet lorsque la boucle s’arrête. Le commit et le push restent à décider séparément.

## Séquence couverture

1. Utiliser un état de démonstration avec une couverture Sonar mesurée et insuffisante.
2. Présenter le taux initial et saisir le pourcentage visé. L’exemple historique visait 80 %, sans imposer cette cible à tous les projets.
3. Cliquer sur **Augmenter la couverture** et laisser le parcours terminer.
4. Montrer les tests ajoutés, les assertions, le résultat du build, le nouveau taux et le périmètre de code conservé.
5. Expliquer un éventuel résultat partiel. Une couverture absente doit être configurée avant cette séquence.

## Séquence Security

1. Présenter la génération et le téléchargement de la SBOM.
2. Cliquer sur **Générer rapport Xray**. Montrer composant, version et constat.
3. Annoncer « contrat Xray simulé par mockXray » si le banc Docker utilise le mock.
4. Lancer la correction. Examiner la dépendance modifiée, le build et les tests, puis la nouvelle SBOM et le nouveau scan.
5. Montrer les états du rapport et rappeler que la disparition d’une anomalie synthétique ne prouve pas une qualification JFrog réelle.

## Séquence GitFlow et MR

1. Relire le diff et utiliser **Commit sélectionné**, puis **Push**, sur le seul dépôt de démonstration.
2. Choisir les branches prévues et demander **Créer une MR** avec un commentaire de développeur.
3. Examiner l’aperçu : commentaire conservé, résumé et recommandation pour chaque fichier, questions éventuelles et vérifications suggérées.
4. Montrer que la pré-revue ne signifie pas approbation. La publication crée un objet partagé dans GitLab : la réserver au dépôt de démonstration prévu.
5. Si le but de la séance est seulement d’examiner la préparation, arrêter au stade de l’aperçu et annoncer qu’aucune MR n’a été publiée.

## Séquence administrateur facultative

Sur un dépôt de gouvernance de démonstration séparé, lancer Mécanique selon son [guide utilisateur](../mecanique/README.md). Ajouter deux projets, modifier une propriété et afficher le brouillon. Montrer les dates, la version incrémentée et les deux fichiers prévus. Ne publier qu’avec les droits et clés du dépôt de démonstration. Vérifier que les autres fichiers n’ont pas changé. Ne montrer ni la clé privée ni des credentials.

## Si la démonstration échoue

- **Politique invalide** : contrôler signature, dates UTC, clé publique approuvée et version. Ne pas désactiver la vérification pour la réunion.
- **Vibe indisponible ou appel échoué** : vérifier runtime, modèle, credentials, réseau et certificats au moyen de traces expurgées. Python 3.12 seul ne prouve pas le fonctionnement complet.
- **Rapport trop volumineux** : réduire le périmètre. La limite est vérifiée avant l’appel IA.
- **Build/tests en échec** : laisser les relances autorisées se terminer, puis présenter le résultat réel. Ne pas modifier manuellement le correctif en prétendant qu’il provient du modèle.
- **Durée trop longue** : arrêter proprement si nécessaire et présenter les résultats historiques en les identifiant comme tels.

La présentation ne contient pas de vidéo d’une nouvelle exécution. Ne pas annoncer « démonstration réussie aujourd’hui » sans avoir réellement constaté la fin du parcours.
