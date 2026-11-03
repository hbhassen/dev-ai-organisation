# Changelog Mécanique

## 1.0.0 — 2026-11-03

- Assistant interactif pour ajouter plusieurs projets, modifier leurs propriétés et ajouter des modèles Mistral.
- Checkout temporaire limité aux deux fichiers de gouvernance ; publication limitée à ces deux fichiers et préservation du reste du dépôt.
- Vérification de la signature existante, renouvellement UTC de 24 heures, incrément de version et signature Ed25519 compatible avec ODS.
- Aperçu avant confirmation, contrôle des modifications concurrentes, push sans force et conservation d'un brouillon en cas d'échec de publication.
- Build, tests et livraison autonomes, sans build ni test de l'extension.
