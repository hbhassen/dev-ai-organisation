# Changelog

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
