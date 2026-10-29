# Evolution 0.3.0 - VSIX autonome et analyses locales

Décision utilisateur du 29 octobre 2026. Cette évolution remplace les prescriptions incompatibles de la version 0.2.1. Elle conserve les starters, les permissions IA par projet, les prompts signés, les diff relus, les tests et GitFlow.

## Décisions acceptées

Le VSIX contient la gateway Java et une configuration organisationnelle préparée au build. Les services et leurs identifiants ne sont pas configurables dans l'interface utilisateur. Les secrets sont masqués dans les diagnostics. L'utilisateur accepte explicitement qu'un secret distribué dans un VSIX reste extractible par le propriétaire du poste : ni une interface en lecture seule, ni une empreinte, ni un chiffrement dont la clé est locale ne constituent une protection contre lui. La gouvernance locale et les quotas locaux ne constituent pas non plus une autorité centrale inviolable.

Le build de démonstration utilise exclusivement des placeholders. Les vraies configurations sont fournies depuis un fichier externe ignoré par Git. Aucun secret réel n'est requis pour compiler ou tester. Les artefacts personnalisés contenant des secrets doivent rester dans la distribution privée autorisée ; ils ne sont pas publiés comme exemples dans Git ou dans les artefacts de CI génériques.

## Exigences nouvelles

### DEVAI-167 - Configuration administrée au build

Le profil fixe les URL GitLab, Mistral API, Mistral Vibe, Sonar et JFrog, les modes d'authentification et les comptes de service. Le VSIX expose seulement un état public sans éditeur de paramètres de service. Les références à des variables externes sont résolues explicitement au packaging embarqué ; une référence manquante bloque ce packaging. Les fichiers source ne sont pas réécrits avec ces secrets. Recette : variations de profil, priorité des valeurs et refus des références absentes ; aucune valeur sensible dans les sorties de build ou la webview.

### DEVAI-168 - Gateway locale possédée par l'extension

La gateway embarquée démarre automatiquement à l'activation, écoute uniquement sur loopback avec un port attribué dynamiquement et exige un jeton aléatoire propre au lancement. L'extension arrête son processus et ses descendants lors de sa désactivation ; la fermeture du canal parent arrête aussi la gateway. Plusieurs fenêtres ne partagent pas implicitement leurs sessions. Aucun processus préexistant n'est arrêté. Recette : démarrage, appels refusés sans jeton, arrêt normal et perte du parent.

### DEVAI-169 - Identité Git sans connexion SSO

Le parcours autonome ne comporte aucune connexion SSO. Avant de démarrer ou connecter la gateway pour un workspace, l'extension vérifie que son remote appartient au GitLab administré et effectue un accès Git réel avec les credentials déjà disponibles dans l'IDE ou Git. Un échec interdit la connexion à la gateway. Un changement de projet invalide cette autorisation ; un dossier non approuvé ne déclenche aucun accès Git. La lecture des projets et de la politique privée utilise le compte de gouvernance configuré au build. GitFlow conserve les credentials Git déjà disponibles au poste ; les MR nécessitent un jeton utilisateur obtenu par un fournisseur d'authentification VS Code ou le gestionnaire de credentials Git. Aucun compte technique n'est substitué pour publier au nom du développeur. En l'absence de credential utilisateur utilisable pour l'API, l'interface explique la limite sans afficher ni demander un secret de service. Le contrôle de l'interface reste contournable par le propriétaire du poste, conformément à la limite acceptée du mode autonome.

### DEVAI-170 - Mistral API ou Vibe

Le build sélectionne API ou VIBE_CLI, VIBE_CLI étant le défaut. Le mode API utilise la clé API administrée. Le mode Vibe accepte la clé API ou le credential Vibe administré selon le mode choisi, avec une URL fixée au build. Aucun fournisseur différent ni compte personnel n'est choisi silencieusement. Les règles, quotas, mesures d'usage et erreurs restent appliqués aux deux transports. Les credentials OAuth interactifs ne sont pas inventés ni assimilés à une clé API exportable.

### DEVAI-171 - Distribution Vibe contrôlée

Le code de la distribution officielle Vibe, sa version figée, ses dépendances et ses licences sont intégrés au VSIX lorsque ce transport est choisi. L'exécution utilise un environnement isolé du .vibe utilisateur et du workspace. Aucun hook, outil ou serveur MCP du projet n'est chargé implicitement. Les actions de l'agent restent soumises aux permissions, outils typés et confirmations ODS. Une dépendance manquante produit un diagnostic, jamais un faux résultat ou un repli silencieux vers l'API.

### DEVAI-172 - SBOM CycloneDX du travail local

Security propose Générer SBOM et permet d'enregistrer un fichier CycloneDX JSON. Java/Maven, .NET/NuGet, Python et Angular/npm sont couverts par leurs générateurs. Aucun commit ni push n'est exigé. Les buffers modifiés doivent être enregistrés avec accord explicite avant un outil qui lit le disque. Les limites du générateur, dépendances non résolues et outils absents sont rapportés. L'empreinte porte sur les entrées analysées et les sorties générées restent distinctes des sources.

### DEVAI-173 - Xray Graph Scan et watch administré

Générer rapport Xray soumet le graphe extrait du SBOM à POST /xray/api/v1/scan/graph?watch=...&scan_type=dependency puis interroge GET /xray/api/v1/scan/graph/{scan_id}?include_vulnerabilities=true&include_licenses=true. Le protocole reprend celui du projet scan-sbom-xray. Le watch est fixé au build. BASIC utilise username/password de service ; TOKEN utilise le token JFrog de service, avec username administratif associé. Le résultat conserve violations, CVE, composants, versions corrigées et chemins d'impact. Timeout, 202, refus, réponse invalide et annulation sont testés avec des doubles.

### DEVAI-174 - Quality sur les sources locales

Le bouton Analyser lance le scanner approprié sur le travail enregistré, y compris sans commit. Le compte Sonar, son token et l'URL proviennent du build. L'analyse n'est jamais simulée par une simple consultation d'un ancien rapport. Le résultat est relié à la tâche Sonar effectivement soumise et à l'empreinte locale. Ce parcours à la demande ne promet pas une analyse continue de chaque frappe comme SonarQube for IDE.

### DEVAI-175 - Remédiation reliée au rapport

Corriger les issues reste disponible pour Sonar et Xray sous réserve du droit IA du projet. Les issues sélectionnées, la provenance du rapport et les fichiers utiles sont transmis comme données non fiables au LLM. Une modification des entrées analysées invalide le rapport pour la remédiation et nécessite une nouvelle analyse. Les changements sont présentés en diff et validés par build/tests puis nouvelle analyse avant toute affirmation de correction.

### DEVAI-176 - Secrets et processus

Les secrets connus, leurs formes d'autorisation et les chemins privés sont filtrés dans les diagnostics. Les erreurs distantes ne recopient pas les corps fournisseurs. Les processus lancés dans un projet ne reçoivent pas les credentials Mistral, de gouvernance ou Xray. Un scanner reçoit uniquement les credentials nécessaires à sa tâche. Les secrets ne sont pas placés dans les arguments de commande ou la webview. Ces mesures limitent les fuites accidentelles sans garantir la confidentialité contre le propriétaire du poste.

### DEVAI-177 - Doubles de tous les services

Les tests remplacent GitLab, Mistral API, Vibe, Sonar, Xray et les processus de scanners par des doubles explicites pour les scénarios déterministes. Ils vérifient les requêtes, authentifications, refus, erreurs et résultats. Des tests de contrat utilisent le code réellement embarqué de Vibe avec un transport simulé. Aucun test ne contacte un service métier réel avec des credentials du poste. Les outils téléchargés pour construire le produit ne sont pas des intégrations métier qualifiées.

### DEVAI-178 - Traçabilité des commentaires

Chaque classe, constructeur et méthode Java/TypeScript/Python écrit pour ODS possède un commentaire explicatif et les identifiants d'exigence pertinents. Les vérificateurs contrôlent l'existence des identifiants dans la spécification ; une revue des responsabilités vérifie leur pertinence. Le code tiers Vibe conserve ses commentaires et sa licence, sans attribution artificielle d'exigences ODS à ses auteurs.

### DEVAI-179 - Envoi effectif depuis IA

Entrée soumet une seule demande non vide lorsque l'IA est autorisée ; Maj+Entrée ajoute une ligne ; la composition IME ne soumet pas. Le bouton Envoyer utilise le même parcours. Le texte reste présent en cas de refus et est effacé après acceptation. Un test traverse le contrôleur réel jusqu'au transport LLM simulé, en complément du test navigateur.

### DEVAI-180 - Changelog généré par le LLM

GitFlow expose un bouton Changelog. La tâche administrée git-changelog exige l'action CHANGELOG dans la politique du projet et le mode MODIFICATION. Elle crée CHANGELOG.md à la racine s'il est absent, ou met à jour sa section non publiée en conservant les versions et informations existantes. Elle inspecte le contenu actuel, l'état Git local et au maximum les 30 derniers commits. Les sources et messages de commit restent des données non fiables. La répétition évite les doublons et n'invente ni version, ticket, test réussi ou publication.

La proposition passe par le diff natif, l'accord utilisateur, le contrôle d'empreinte et le checkpoint existants. Le client limite l'écriture à ce seul fichier et interdit sa suppression ; un refus, un conflit, une panne LLM ou une simple réponse textuelle ne doivent pas être annoncés comme une modification réussie. Les fichiers non enregistrés sont sauvegardés avec l'accord existant. Le mode LOCAL_GOVERNED est requis pour cette tâche locale. Aucun commit, push, tag ou release n'est créé.

**Recette :** tester la création, la mise à jour avec historique conservé, le refus d'écriture hors cible, les permissions manquantes, les erreurs/refus sans modification et le déclenchement du bouton avec un LLM simulé.

### DEVAI-181 - Notes de release générées par le LLM

GitFlow expose un bouton Release notes. La tâche administrée git-release-notes exige l'action RELEASE_NOTES et suit le même parcours que DEVAI-180 pour RELEASE_NOTES.md. Elle crée le fichier absent ou actualise son brouillon sans supprimer les anciennes notes. Elle décrit les changements, impacts et migrations attestés ; les tests non vérifiés restent indiqués comme tels. Sans version de release confirmée, la section reste un brouillon Non publié. La génération ne publie aucune release distante.

**Recette :** vérifier création et mise à jour, maintien des notes antérieures, refus de suppression ou d'une autre cible, droits distincts de CHANGELOG, diff et application contrôlée avec réponses LLM simulées.

## Prompt d'implémentation

Lire specification.md et ce document, puis examiner les composants existants et les tests avant modification. Appliquer DEVAI-167 à DEVAI-181 et réviser les exigences antérieures incompatibles sans réutiliser leurs identifiants. Conserver la compatibilité VS Code 1.95.2 et les scripts batch sans PowerShell. Vérifier le protocole Xray dans scan-sbom-xray et les contrats officiels de la version Vibe figée. Implémenter les parcours complets : configuration au build, gateway automatique, credentials Git natifs, deux transports Mistral, SBOM quatre stacks, Graph Scan avec watch, Sonar local, remédiations, envoi Entrée et génération contrôlée de CHANGELOG.md / RELEASE_NOTES.md. Documenter la limite de confidentialité acceptée, les prérequis et chaque cas non vérifiable. Ajouter les commentaires avec références pertinentes sur chaque nouveau symbole et corriger les commentaires devenus faux. Tester avec des doubles, exécuter le build complet, vérifier le VSIX installé et les arrêts de processus, actualiser le PDF, puis publier uniquement les sources et artefacts de démonstration validés sans secret réel.
