# Profil Java générique
Origine : GENERIC. Exigences : DEVAI-005, DEVAI-048, DEVAI-137, DEVAI-162, DEVAI-163.

Utiliser Java 21 pour les nouveaux composants ODS et Maven avec le wrapper du projet. Respecter la version Spring Boot qualifiée et ne pas introduire Gradle. Séparer domaine, cas métier et adaptateurs. Valider les entrées à la frontière ; utiliser des exceptions explicites sans exposer de secrets. Éviter état global mutable et dépendances inutiles. Ajouter une Javadoc utile à chaque classe, record, interface, constructeur et méthode avec les références DEVAI pertinentes. Écrire des assertions métier, couvrir limites et refus, puis exécuter le build Maven complet. Ne pas désactiver des tests pour réussir le build.
