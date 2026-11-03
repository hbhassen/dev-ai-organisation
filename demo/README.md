# Démonstration et présentation ODS

**Édition du support : 1.0, 3 novembre 2026.** Périmètre : extension ODS 0.6.0 et Mécanique 1.0.0, examinées au commit `385ea41b61d77a01ecb9a58f98deb59d356d77ef`.

Ce dossier aide à présenter la solution aux développeurs, reviewers, responsables de plateforme, sécurité et management. Il propose un pilote mesuré et distingue les fonctions disponibles, les preuves historiques et les travaux restant à qualifier.

## Fichiers à utiliser

| Fichier | Usage |
|---|---|
| [ODS-Presentation-Technico-Commerciale.pptx](ODS-Presentation-Technico-Commerciale.pptx) | PowerPoint 16:9, 30 diapositives modifiables avec notes orales |
| [PITCH.md](PITCH.md) | Pitch de 90 secondes, version de 8 à 10 minutes et réponses aux objections |
| [NOTES_ORATEUR.md](NOTES_ORATEUR.md) | Notes détaillées par diapositive et sources du dépôt |
| [DEMONSTRATION.md](DEMONSTRATION.md) | Préparation et conduite d’une démonstration réelle |
| [PILOTE.md](PILOTE.md) | Périmètre, responsabilités, mesures et décision d’adoption |
| [SOURCES.md](SOURCES.md) | Traçabilité des affirmations, dates et limites des preuves |
| [slides.json](slides.json) | Contenu éditable du support, notes et références |
| [build-presentation.mjs](build-presentation.mjs) | Générateur du PowerPoint, indépendant du build de l’extension |

## Parcours de présentation

- **Réunion complète** : diapositives 1 à 24, environ 25 à 35 minutes, puis questions. Les diapositives 25 à 30 servent d’annexes.
- **Présentation courte** : utiliser le pitch de 90 secondes puis les diapositives 3, 10, 19, 21 et 24.
- **Discussion technique** : approfondir l’architecture, la gouvernance, Mécanique et les limites dans les annexes.
- **Démonstration** : ajouter une séquence séparée avec le guide fourni. Le dossier ne prétend pas qu’une nouvelle démonstration a été exécutée pendant sa rédaction.

Les tableaux, le graphique et les textes sont modifiables dans PowerPoint. La capture de l’interface est une image issue de la webview 0.6.0 avec données synthétiques. Elle illustre le produit, sans prouver un appel Mistral réel ni une consommation effective.

## Points à adapter avant la réunion

Nommer le sponsor, les responsables et les projets candidats. Les hypothèses de pilote restent des propositions. Ne pas remplacer les limites par des promesses de production : Xray réel, réseau organisationnel, droits de diffusion et gestion des secrets doivent être qualifiés. Aucun tarif, ROI, certification ou gain de productivité n’a été inventé.

Le manifeste du produit déclare `UNLICENSED`. Ce support ne définit pas de nouvelle licence et n’accorde aucun droit sur les composants tiers.

## Modifier ou régénérer le support

Pour une adaptation ponctuelle, ouvrir directement le PowerPoint et conserver une copie de travail. Pour une mise à jour reproductible, modifier `slides.json` et le script de mise en page, puis régénérer avec **Node.js et le runtime fourni contenant `@oai/artifact-tool` 2.8.106**. Ce runtime n’est pas livré dans le dépôt et n’est pas nécessaire pour lire le PowerPoint.

Le script nécessite les variables suivantes, toutes fournies par votre environnement local :

```text
RUNTIME_NODE_MODULES=<RUNTIME_NODE_MODULES>
RUNTIME_PYTHON=<RUNTIME_PYTHON_EXECUTABLE>
PRESENTATIONS_SKILL_DIR=<PRESENTATIONS_SKILL_DIR>
DEMO_REVISION=<NOUVEL_IDENTIFIANT>
```

Lancer ensuite :

```text
node demo/build-presentation.mjs
```

Le générateur crée un brouillon, exécute les contrôles du package et de la géométrie, réimporte le PowerPoint final, rend chaque diapositive et écrit les notes. Les fichiers intermédiaires et rendus restent dans `.artifacts/demo-presentation/`, ignoré par Git. Le finaliseur refuse d’écraser un fichier final existant : utiliser un nouvel identifiant de révision.

Après inspection visuelle des rendus, copier le fichier final validé à la place du PowerPoint de ce dossier. Le script ne construit, ne teste, n’installe ni ne publie l’extension. Aucun appel Mistral n’est nécessaire pour créer la présentation.

## Historique

Voir [CHANGELOG.md](CHANGELOG.md). Ce dossier contient des supports de communication, sans nouvelle version applicative, VSIX, tag ou release du produit.
