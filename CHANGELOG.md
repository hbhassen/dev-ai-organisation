# Changelog

## 0.3.2 — 2026-10-31

- Quality et Security : « Corriger les issues » lance directement l’agent Mistral pour les issues sélectionnées, applique le correctif avec checkpoint récupérable puis ouvre le diff, sans consigne complémentaire ni validation du patch.
- Deux relances automatiques au maximum si Mistral répond sans appliquer de correctif ; absence de changement et erreurs signalées explicitement.
- Protection contre les corrections concurrentes, les rapports obsolètes et les buffers modifiés ; synchronisation du mode Agent et de l’onglet IA.
- Tests sans appel fournisseur pour les deux parcours, annulation, conflits, absence de patch et échec d’ouverture du diff. Les autres tâches conservent leur revue avant application.
- L’agent peut exécuter Maven installé (`mvn`) pour les projets sans wrapper, avec l’adaptation Windows et les mêmes contrôles d’autorisation et de métacaractères que les autres commandes.

## 0.3.1 — 2026-10-31

- Correction du bridge Vibe sous Windows : restauration du répertoire courant avant suppression du dossier temporaire ; une réponse réussie n'est plus perdue pendant le nettoyage.
- Erreurs de clé, quota et modèle distinguées des erreurs de runtime, sans exposer les diagnostics privés du fournisseur.
- Correction du contrat JSON HTTP de `workspace/fingerprint`, utilisé par les contrôles avant remédiation.
- Tests de régression du point d'entrée CLI Vibe et de la sérialisation HTTP du gateway ; simulations réseau sans appel Mistral réel par défaut.

## Banc de test Docker local — 2026-10-31 (base 0.3.0, sans release)

- Ajout de `TestDOKERMan/` : GitLab CE, SonarQube et mock Graph Scan Xray en HTTPS local, avec comptes, projets de démonstration autorisé/refusé et politique Ed25519 renouvelable.
- Profil privé de build Mistral Medium 3.5 et profil VS Code `Test` isolé ; les validations automatiques du banc ne font aucun appel de génération Mistral.
- Sources applicatives existantes inchangées. Secrets, données Docker et livrables privés exclus de Git ; bilan effectif dans `TestDOKERMan/VALIDATION.md`.
- Ancien `suiteInstall/` retiré du suivi Git sur cette branche, sans suppression des fichiers locaux ni réécriture de l'historique ; la livraison privée du banc reste un artefact ignoré.

## Préparation intégration HMSile — 2026-10-29 (base 0.3.0)

- Ajout des modèles `integration/` pour HMSile dev IA : Mistral/Vibe, Xray, Sonar, GitLab, gouvernance et mappings de projets, avec URL fictives et placeholders.
- Guide de configuration privée, compilation et installation du VSIX personnalisé ; aucun build ni test relancé pour cette préparation.
- Instantané `suiteInstall/` de démonstration 0.3.0 exceptionnellement versionné sur `hm/integration`, sans reconstruction ni changement de sa provenance `e89936a`.


## 0.3.0 — 2026-10-29

- GitFlow : boutons Changelog et Release notes pour créer ou mettre à jour les documents dédiés via le LLM, avec droits distincts, contrôle de cible et validation du diff.

- Connexion du VSIX sans SSO : contrôle Git réel avant démarrage du gateway embarqué, session privée loopback et arrêt possédé, y compris si la fermeture intervient pendant une connexion en cours.
- Configuration organisationnelle et credentials embarqués au build, interface verrouillée et masquage ; extractibilité des secrets explicitement acceptée.
- Choix Mistral API ou adaptateur CLI utilisant le backend officiel Vibe 3.0.3 embarqué, Vibe par défaut.
- SBOM CycloneDX Java, Angular, Python et .NET exportable sans commit ; Graph Scan Xray avec watch et authentification Basic ou token.
- Analyse Sonar locale sans commit, résultat lié à la tâche soumise et à une empreinte des sources ; refus des remédiations obsolètes.
- Tests par doubles des nouveaux échanges, envoi Entrée jusqu’au client HTTP, documentation, spécification et PDF révisés.
- Prérequis de distribution : Python 3.12 et VSIX pour la plateforme cible ; scanners de stack à installer. La rotation des credentials requiert un nouveau build privé.


## 0.2.1 — 2026-10-29

- Correction du refus d'installation sur VS Code 1.95.2 : minimum déclaré abaissé de 1.96.0 à 1.95.0, pour la série VS Code 1.x.
- Compilation avec les définitions d'API `@types/vscode` 1.95.0 verrouillées, sans ajout d'API ni modification des fonctions de l'extension.
- Documentation d'installation actualisée ; les anciens VSIX doivent être remplacés par le package reconstruit 0.2.1. Les versions antérieures à VS Code 1.95.0 ne sont pas prises en charge.
- Suppression des appels PowerShell des scripts `.bat` et du wrapper `mvnw.cmd` : orchestration Python 3.11+, Maven 3.9.11 vérifié par SHA-256, validation et packaging conservés.
- Lanceur de gateway en Python, avec conservation des configurations littérales et des références aux variables d'environnement ; Python 3.11+ est désormais requis pour ce lanceur sur la machine cible.
- Tests du build, de la préservation des anciennes livraisons, des empreintes et de la configuration ; instantané `suiteInstall/` exceptionnellement versionné sur `script/vsix` à la demande de l'utilisateur.

## 0.2.0 — 2026-10-29

- Interface à cinq onglets avec icônes, badges, barre de quota et navigation clavier, adaptée aux panneaux étroits.
- Saisie IA avec suggestions, modes, envoi clavier, arrêt et prévention des doubles soumissions ; brouillon conservé si la demande est refusée.
- Sélection multifichier avec aperçu et consentement ; contexte serveur par références de commit, refus des buffers non enregistrés dans ce mode.
- Sélection des fichiers du diff avant application et recalcul du plan ; révocation des accords lors d’un changement de périmètre du plan.
- Navigation dans les pages Sonar, liste de résultats Xray et préremplissage de la description de MR sur une révision inchangée.
- `script.bat <profil.json>` : reconstruction intégrale et surcharge du nom du plugin, de son identité, de la gateway et des fichiers de configuration serveur, sans modifier les sources.
- Tests d’intégration HTTPS de GitLab, Mistral/SSE, Sonar, Xray et runner avec JWT OIDC et politique signée ; scénarios d’erreur et de refus.
- Annulation d’une demande encore en soumission lorsque le projet se ferme ; blocage des interactions concurrentes et des doubles départs.
- Tests navigateur de la webview et tests natifs des nouvelles fonctions ; documentation et matrice actualisées.
- Publication de développement et instantané de livraison sur `dev/toreprise`, selon l’exception demandée ; aucune configuration réelle d’organisation fournie.

## 0.1.0 — 2026-10-29

- Création de l'extension à cinq vues, du moteur Java 21 et de la gateway Maven/Spring Boot.
- OIDC/PKCE, SecretStorage par gateway, vérification GitLab, politiques Ed25519 et refus par défaut.
- Catalogue signé des modèles Mistral exacts, rotation/révocation des clés de confiance.
- Quotas atomiques par utilisateur et projet, checkpoints chiffrés et reprise après perte de bail.
- Adaptateur Mistral, boucle d'outils, modes Question/Modification/Agent, quotas persistants et idempotence.
- Instructions globales/par stack, tâches organisationnelles signées et contrôle des conflits structurés.
- Starters Spring Initializr/Maven, Angular CLI, Python PyPA et .NET ; remplacements et overlays signés.
- Diffs, détection des bases obsolètes, copies de récupération, processus bornés et commandes Git contrôlées.
- Clone contrôlé, restauration de checkpoints, sélection de remédiations et préparation de descriptions de MR.
- Connecteurs de rapports Sonar/Xray et orchestration de pipelines GitLab dédiés aux scans.
- Scripts `.bat` de build, de VSIX et de livraison autonome ; contrôles des commentaires et tests.
- Qualification Linux/PostgreSQL ; correction du test de chemins canoniques pour les noms courts Windows.
- Identité Git capturée avant compilation ; refus de livraison si le commit change pendant le build et refus de release depuis des sources modifiées.
- Révocation immédiate du contexte local et arrêt du moteur lorsqu'une racine de workspace est fermée.

La qualification des services organisationnels est distincte du build local. Consulter la matrice de conformité et le rapport de validation pour connaître les vérifications réellement exécutées et les limitations restantes.
