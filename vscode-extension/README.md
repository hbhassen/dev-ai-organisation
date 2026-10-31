# Organisation Development Standard

Cinq onglets : Starter, IA, Quality, Security et GitFlow. Compatible VS Code Desktop 1.95.0+ dans la série 1.x, dont 1.95.2.

Prérequis : Java 21, Python 3.12, Git et les outils de la stack. La configuration de l’organisation, le gateway et Vibe sont embarqués au build. Ouvrir un workspace approuvé puis ODS : l’accès Git existant est vérifié avant démarrage automatique du gateway. Aucun SSO, URL ou secret de service n’est demandé au développeur.

Security génère un SBOM CycloneDX téléchargeable puis un rapport Xray. Quality analyse les fichiers enregistrés. Ces parcours fonctionnent avant un commit. « Corriger les issues » lance Mistral pour la sélection validée, applique le correctif avec sauvegarde puis ouvre le diff, sans consigne complémentaire. Build, tests et nouvelle analyse restent nécessaires pour valider la correction. GitFlow utilise le compte Git du développeur.

Le mode Vibe utilise le backend officiel embarqué via un adaptateur CLI isolé ; ses outils interactifs ne remplacent pas les approbations ODS. Les secrets compilés restent extractibles par le propriétaire du poste malgré le verrouillage de l’interface et le masquage des logs. Distribuer le VSIX personnalisé uniquement dans un canal privé autorisé.

GitFlow includes Changelog and Release notes actions. They create or update `CHANGELOG.md` and `RELEASE_NOTES.md` through the managed LLM and reviewed changes. The signed policy must grant `CHANGELOG` or `RELEASE_NOTES`; generation never publishes a release.
