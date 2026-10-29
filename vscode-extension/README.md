# Organisation Development Standard

Cinq onglets : Starter, IA, Quality, Security et GitFlow. Compatible VS Code Desktop 1.95.0+ dans la série 1.x, dont 1.95.2.

Prérequis : Java 21, Python 3.12, Git et les outils de la stack. La configuration de l’organisation, le gateway et Vibe sont embarqués au build. Ouvrir un workspace approuvé puis ODS : l’accès Git existant est vérifié avant démarrage automatique du gateway. Aucun SSO, URL ou secret de service n’est demandé au développeur.

Security génère un SBOM CycloneDX téléchargeable puis un rapport Xray. Quality analyse les fichiers enregistrés. Ces parcours fonctionnent avant un commit. Les corrections IA sont gouvernées, présentées en diff et à valider par build, tests et nouvelle analyse. GitFlow utilise le compte Git du développeur.

Le mode Vibe utilise le backend officiel embarqué via un adaptateur CLI isolé ; ses outils interactifs ne remplacent pas les approbations ODS. Les secrets compilés restent extractibles par le propriétaire du poste malgré le verrouillage de l’interface et le masquage des logs. Distribuer le VSIX personnalisé uniquement dans un canal privé autorisé.
