# Profil Python générique
Origine : GENERIC. Exigences : DEVAI-050, DEVAI-137, DEVAI-162, DEVAI-163.

Utiliser pyproject.toml, une structure src et un environnement virtuel isolé. Déclarer les dépendances et qualifier leurs versions ; aucune installation globale implicite. Employer des types lisibles, des fonctions ciblées et des exceptions explicites. Toutes les classes et fonctions possèdent une docstring expliquant paramètres, résultats, erreurs pertinentes et exigences. Tester les entrées invalides et le package installé, pas seulement l'import depuis le dossier source. Ne pas inclure credentials, fichiers de poste ou caches dans le wheel.
