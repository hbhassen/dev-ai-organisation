# Spécification complète — Organisation Development Standard (ODS)

**Version documentaire : 1.4 — 29 octobre 2026 ; version produit cible : 0.3.0.**

## 1. Référentiel et décisions

Ce référentiel remplace la conception 1.2 pour les parcours modifiés par la demande utilisateur du 29 octobre 2026. Le PDF `Organisation_Development_Standard_Architecture_avec_Interface.pdf` et le prompt d’implémentation doivent rester cohérents avec ces exigences. Les recettes décrivent les résultats attendus ; les résultats réellement exécutés sont consignés dans `docs/VALIDATION.md`.

Le produit vise VS Code Desktop Windows et Linux, minimum 1.95.0 (dont 1.95.2), Java 21, Maven, TypeScript et Python 3.12 pour le runtime Vibe et les scanners locaux. Les cinq vues Starter, IA, Quality, Security et GitFlow sont conservées. Les fournisseurs IA autres que Mistral restent interdits par défaut.

Le VSIX est autonome vis-à-vis de l’arborescence source et contient le gateway, Vibe et sa configuration administrée. Les runtimes Java/Python et les outils des stacks restent des prérequis du poste. Le mode distribué ne comporte aucune connexion SSO. Un accès Git réel avec les credentials existants du développeur conditionne la connexion au gateway. Les credentials de service sont intégrés au build, en lecture seule dans le parcours utilisateur et masqués dans les logs. **Le propriétaire du poste peut les extraire ; cette limite est explicitement acceptée.**

Les scans portent sur les sources enregistrées et ne nécessitent pas de commit. Les résultats et remédiations sont rattachés à une empreinte locale. La gouvernance signée continue de distinguer les droits IA par projet ; les scans non-IA restent disponibles indépendamment de ces droits. Les destinations et secrets réels doivent être fournis par l’organisation et ne sont jamais inventés ou récupérés sur le poste pour les tests.

Les exigences DEVAI-001 à DEVAI-166 sont conservées avec leurs identifiants ; les sections modifiées remplacent leur prescription antérieure. DEVAI-167 à DEVAI-181 détaillent le mode autonome. Les configurations du serveur séparé historique ne sont pas un prérequis du VSIX. L’intégrité locale détecte une corruption accidentelle, sans garantir l’inviolabilité du poste.

## 2. Cadrage et architecture — DEVAI-001 à DEVAI-012

### DEVAI-001 — Périmètre fonctionnel complet

**Origine historique (PDF 1.0) :** PDF p. 2–6. **Responsable :** produit / extension / moteur.

ODS DOIT exposer les cinq onglets Project Initializer, IA Code Generator, Quality, Application Security et GitFlow. Les fonctions non IA restent disponibles selon leurs propres droits quand l'IA est interdite. La version complète inclut les quatre lignes Java, Python, .NET et Angular ; un lot initial ne vaut pas livraison complète.

**Recette :** un projet non éligible à l'IA peut accéder au catalogue, à ses analyses et aux opérations Git autorisées ; tous les chemins d'appel IA sont refusés.

### DEVAI-002 — Architecture locale à trois composants

**Origine :** évolution utilisateur du 29 octobre 2026.

Le VSIX contient une extension TypeScript, le moteur Java 21 sur stdio et une gateway Spring Boot possédée par l’extension. Le contrôle Git natif précède tout démarrage du gateway. Le gateway écoute sur loopback et exige le jeton aléatoire de lancement. Les intégrations de service passent par lui ; GitFlow conserve les credentials du développeur. Voir DEVAI-167 — DEVAI-176.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-003 — Frontières de confiance du mode autonome

**Origine :** évolution utilisateur du 29 octobre 2026.

La webview ne reçoit ni secrets ni chemins natifs de configuration. Le client natif vérifie Workspace Trust, le remote GitLab administré et un accès Git réel. La gateway limite les routes de projet au dépôt vérifié au lancement. La politique IA signée demeure contrôlée par la gateway. Le propriétaire du poste peut extraire les secrets et modifier le programme distribué ; cette limite acceptée interdit de présenter quotas et gouvernance locaux comme une autorité centrale inviolable.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-004 — Modularité et dépendances

**Origine historique (PDF 1.0) :** PDF p. 10 ; Conception. **Responsable :** tech lead Java.

Le domaine DOIT être indépendant des clients HTTP et du transport IPC. Les modules sécurité, configuration, starters, contexte IA, qualité, Xray et GitFlow utilisent des ports typés et des adaptateurs testables. Les règles communes de signature et de décision peuvent être partagées entre moteur et serveur, mais la gateway charge elle-même les données officielles. Les DTO publics ne révèlent pas les modèles de persistance.

```mermaid
sequenceDiagram
    participant API as Adaptateur REST ou IPC
    participant UC as Cas métier Java
    participant Port as Port de domaine
    participant Adapter as Adaptateur GitLab
    API->>UC: DTO validé
    UC->>Port: Résoudre le projet
    Port->>Adapter: Implémentation injectée
    Adapter-->>UC: Projet normalisé
    UC-->>API: Résultat métier
```

**Recette :** un connecteur GitLab de test remplace le connecteur réel sans modifier le domaine ; aucune dépendance circulaire entre modules n'est acceptée.

### DEVAI-005 — Socle Java et Maven

**Origine historique (PDF 1.0) :** PDF p. 11 ; Utilisateur. **Responsable :** build Java.

Le moteur et la gateway DOIVENT cibler Java 21 et être construits par Maven. Le dépôt DOIT fournir `pom.xml`, `mvnw`, `mvnw.cmd` et les métadonnées du wrapper avec version et empreinte de distribution. Spring Boot 4.x est la famille cible de la gateway ; la version exacte compatible est choisie, documentée et figée à l'implémentation. Gradle n'est pas proposé pour un nouveau starter Java. Un projet existant Gradle peut être détecté sans conversion silencieuse ni promesse de prise en charge non qualifiée.

**Recette :** un checkout propre compile et teste les modules Java avec le wrapper en Windows et Linux ; aucun module Java ne dépend d'un build Gradle.

### DEVAI-006 — Plateformes et prérequis

**Origine historique (PDF 1.0) :** PDF p. 11 ; Conception. **Responsable :** extension / packaging.

Le support V1 DOIT couvrir VS Code Desktop Windows et Linux. L'extension vérifie Java 21 et Git avant leur utilisation, ainsi que les SDK nécessaires au starter demandé. Elle affiche une erreur exploitable si un runtime manque, sans installation silencieuse. Les environnements distants non qualifiés sont détectés et déclarés non pris en charge. Un JRE fourni séparément reste une option soumise aux licences et à la qualification.

**Recette :** absence de Java, mauvaise version, absence de Python et environnement distant produisent des diagnostics distincts ; les vues statiques restent consultables.

### DEVAI-007 — Séparation produit et hébergement du code

**Origine historique (PDF 1.0) :** PDF p. 6–7 ; règles de développement utilisateur ; Conception. **Responsable :** architecture / livraison.

Le produit DOIT utiliser GitLab pour l'identité de projet, les politiques, les starters organisationnels et les MR. Le dépôt qui contient le code d'ODS peut être hébergé sur GitHub privé conformément aux règles du développeur. Cette différence n'autorise ni à remplacer GitLab par GitHub dans les fonctionnalités ni à inventer une infrastructure disponible. La CI et la publication utilisent le fournisseur réellement configuré.

**Recette :** les configurations distinguent clairement dépôt de développement, GitLab métier et dépôt de gouvernance ; aucun endpoint GitHub ne remplace une MR GitLab.

### DEVAI-008 — Catalogue et versions externalisés

**Origine historique (PDF 1.0) :** PDF p. 5, 9–11. **Responsable :** configuration / gouvernance.

Les versions des starters, modèles IA, prompts, scanners et SDK DOIVENT provenir de manifestes administrés, signés et versionnés. Les versions de construction d'ODS sont fixées dans ses fichiers de build. Une version de manifeste n'est ni la version de l'application ni celle du schéma. Aucun `latest` implicite ne doit affecter une génération reproductible.

**Recette :** changer un alias de modèle ou une version autorisée de starter n'impose pas une nouvelle publication du VSIX ; le résultat conserve les versions effectivement résolues.

### DEVAI-009 — Administration gouvernée

**Origine historique (PDF 1.0) :** PDF p. 6–9, 12. **Responsable :** administrateurs / CI gouvernance.

Les politiques, catalogues et prompts de production DOIVENT être gérés dans des dépôts GitLab privés protégés, séparés des exemples du code applicatif. Les changements passent par revue, validation de schéma, tests et signature. La gestion des rôles et la séparation auteur / approbateur sont appliquées selon les capacités de GitLab et les règles organisationnelles.

**Recette :** un développeur ordinaire ne peut publier une politique ; une donnée non validée ou non signée ne devient pas active.

### DEVAI-010 — Responsabilité humaine

**Origine historique (PDF 1.0) :** PDF p. 5–6, 9 ; Conception. **Responsable :** moteur / extension.

Un résultat IA DOIT être présenté comme une proposition. Les écritures, commandes, installations, suppressions, push et créations de MR passent par une décision d'exécution explicite ou une autorisation de session précise, limitée et journalisée prévue par politique. Un simple consentement au chat ne vaut pas consentement à ces effets de bord. Aucun résultat d'analyse ne peut être déclaré corrigé avant vérification.

**Recette :** la demande « explique ce code » ne modifie aucun fichier et ne lance aucune commande.

### DEVAI-011 — Terminologie et erreurs compréhensibles

**Origine historique (PDF 1.0) :** PDF p. 3 ; Conception. **Responsable :** UX / contrats.

L'interface DOIT distinguer « IA interdite », « authentification requise », « politique invalide », « service indisponible », « quota atteint », « rapport obsolète » et « fonctionnalité non prise en charge ». Les messages affichent un identifiant de corrélation et une action de résolution sans exposer secrets, chemins absolus ou détails internes sensibles.

**Recette :** chaque code d'erreur du contrat possède un libellé et un comportement de reprise testés.

### DEVAI-012 — Définition de livraison complète

**Origine historique (PDF 1.0) :** PDF p. 11–12 ; Utilisateur ; Conception. **Responsable :** tech lead / recette.

Une livraison complète DOIT associer chaque exigence à une implémentation, des tests et une preuve. Les connecteurs réels, quatre starters, cinq vues, paquet d'installation, documentation et limites connues font partie de la livraison. Un mode démonstration à données simulées doit être étiqueté et ne satisfait pas les validations d'intégration réelle.

**Recette :** la matrice de traçabilité ne contient aucun « réussi » sans preuve ; les contrôles impossibles sont marqués `NON VÉRIFIABLE` avec leur dépendance manquante.

<a id="section-3"></a>

## 3. Extension, IPC et identité — DEVAI-013 à DEVAI-024

### DEVAI-013 — Bandeau et navigation

**Origine historique (PDF 1.0) :** PDF p. 3 et maquette. **Responsable :** extension.

Le bandeau DOIT afficher le projet GitLab actif, sa branche, sa stack, l'état de connexion, le statut IA, l'alias du modèle autorisé et le quota restant. Les cinq onglets partagent ce contexte. L'interface différencie chargement, vide, erreur, déconnecté et accès refusé. Les changements de projet invalident immédiatement les propositions devenues étrangères au contexte.

**Recette :** une bascule de racine met à jour le bandeau et les commandes sans afficher un quota ou une permission provenant de la racine précédente.

**Précision utilisateur 1.2 :** conserver cinq onglets de largeur égale dans l’ordre **Starter, IA, Quality, Security, GitFlow**, chacun avec une icône monochrome et un état sélectionné accessible. Le bandeau présente l’organisation, le projet, la branche, les badges de connexion/autorisation ; la carte IA affiche le contexte et la consommation.

### DEVAI-014 — Contrat JSON-RPC local

**Origine historique (PDF 1.0) :** PDF p. 4, 11 ; Conception. **Responsable :** extension / moteur.

Le transport local DOIT utiliser JSON-RPC 2.0 sur les flux stdio d'un processus enfant appartenant à l'extension. Le framing ODS est un objet JSON UTF-8 par ligne, avec retours à la ligne des chaînes échappés ; taille maximale initiale de 8 Mio par message et refus des lots JSON-RPC en V1. `stdout` est réservé au protocole, `stderr` aux logs filtrés. L'initialisation négocie version de protocole, version moteur et capacités ; les contrats sont validés des deux côtés. Les annulations et notifications sont des méthodes ODS documentées, pas des mécanismes implicites de JSON-RPC. Référence : [JSON-RPC 2.0](https://www.jsonrpc.org/specification).

```mermaid
sequenceDiagram
    participant E as Extension
    participant J as Processus Java
    E->>J: initialize(id, protocolVersion)
    J-->>E: result(protocolVersion, engineVersion)
    E->>J: project.resolve(id, workspaceId)
    J-->>E: result(projectContext)
    E->>J: scan.start(id, projectContext)
    J-->>E: result(jobId)
    J-->>E: job.progress(jobId, status)
    E->>J: job.cancel(id, jobId)
    J-->>E: result(cancelRequested)
```

**Recette :** messages fragmentés, UTF-8, requête inconnue, dépassement de taille et désaccord de version sont testés sans blocage de l'extension.

### DEVAI-015 — Cycle de vie du moteur

**Origine :** Conception. **Responsable :** extension / moteur.

L'extension DOIT démarrer un seul moteur par fenêtre VS Code, avec le chemin absolu du JAR installé résolu par ses API, sans commande shell assemblée. Elle négocie le protocole avant usage et termine proprement le processus à la désactivation. Après crash, au plus trois redémarrages avec délai progressif sont tentés ; les actions non idempotentes ne sont jamais rejouées automatiquement.

```mermaid
sequenceDiagram
    participant E as Extension
    participant J as Java
    E->>J: Démarrage et handshake
    J-->>E: READY
    Note over E,J: Crash éventuel
    E->>E: Marquer les requêtes en erreur
    E->>J: Redémarrage borné
    J-->>E: READY
    E->>J: Réévaluer projets et droits
```

**Recette :** un crash pendant une génération ne double ni écriture ni appel IA ; les processus enfants ne restent pas orphelins après fermeture.

### DEVAI-016 — Workspace Trust

**Origine historique (PDF 1.0) :** PDF p. 8–9 ; Conception. **Responsable :** extension / moteur.

Dans un workspace non approuvé, ODS DOIT désactiver lecture de contexte destinée à l'IA, exécution de scripts, génération dans ce workspace et commandes Git à effet de bord. Il peut afficher aide et catalogue sans exécuter le contenu du projet. Le changement de confiance déclenche une réévaluation. Cette information locale ne constitue pas une attestation serveur d'intégrité du poste. Référence : [VS Code Workspace Trust](https://code.visualstudio.com/api/extension-guides/workspace-trust).

**Recette :** l'ouverture d'un dépôt contenant des scripts de build n'en exécute aucun avant approbation du workspace.

### DEVAI-017 — Isolation des racines et dépôts imbriqués

**Origine historique (PDF 1.0) :** PDF p. 7, 12 ; Conception. **Responsable :** moteur.

Chaque racine et dépôt imbriqué DOIT être résolu séparément. La sélection active détermine un contexte de projet explicite ; tout fichier d'un autre dépôt, sous-module ou worktree est réévalué. La clé de contexte inclut racine canonique locale, instance, project ID, branche et commit ; seuls les identifiants nécessaires quittent le poste.

**Recette :** avec deux racines autorisée / interdite, les conversations, sélections, propositions et quotas ne se mélangent pas.

### DEVAI-018 — Sécurité des webviews

**Origine :** Conception. **Responsable :** extension.

Les webviews DOIVENT utiliser une CSP restrictive, des ressources locales autorisées explicitement et des messages validés par schéma. Le HTML généré par le modèle ou un rapport est neutralisé ; les liens externes demandent une action utilisateur. Les jetons et secrets ne transitent pas dans le DOM, les URI ou l'état persistant des vues. Référence : [API Webview VS Code](https://code.visualstudio.com/api/extension-guides/webview).

**Recette :** un rapport contenant du script ou un lien de commande ne peut exécuter de code ni appeler une commande privilégiée.

### DEVAI-019 — Accessibilité et expérience native

**Origine historique (PDF 1.0) :** PDF p. 3 ; Conception. **Responsable :** extension.

Les vues DOIVENT respecter les thèmes VS Code, le clavier, l'ordre de focus, les noms accessibles et les états compréhensibles sans couleur seule. Les résultats ouvrent fichier et ligne avec les API éditeur ; une référence invalide affiche un diagnostic. L'interface initiale est en français, avec libellés métier des cinq onglets conservés et textes externalisés.

**Recette :** un parcours initialisation / chat / rapport / diff fonctionne au clavier en thème clair et sombre ; les raisons de refus sont lisibles par lecteur d'écran.

**Précision utilisateur 1.2 :** la saisie du chat privilégie une zone compacte inspirée des assistants IDE, avec trombone, sélection du mode et bouton d’envoi. Entrée envoie, Maj+Entrée insère une ligne ; empêcher les doubles soumissions et conserver un brouillon refusé.

### DEVAI-020 — Connexion fondée sur Git, sans SSO

**Origine :** évolution utilisateur du 29 octobre 2026.

Aucun écran, commande ou paramètre SSO n’est utilisé par le VSIX autonome. Avant connexion, le client natif vérifie réellement l’accès Git avec les credentials déjà présents dans Git ou l’IDE. Un refus interdit le lancement et la connexion au gateway. Voir DEVAI-169. Le support OIDC historique d’un serveur déployé séparément n’est pas activé par ce parcours.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-021 — Conservation de la session locale

**Origine :** évolution utilisateur du 29 octobre 2026.

Le jeton de connexion au gateway est aléatoire, privé au lancement et non persisté. Les données locales sont chiffrées avec une clé stable du profil ou générée dans SecretStorage. La déconnexion arrête le processus possédé et conserve les credentials Git de l’IDE. Une session ne change jamais implicitement de workspace.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-022 — Accès au dépôt et compte de lecture

**Origine :** évolution utilisateur du 29 octobre 2026.

Le contrôle Git du développeur a lieu dans le client natif avant connexion. Le compte de gouvernance configuré au build lit les métadonnées et politiques privées ; il doit avoir les droits de lecture nécessaires. La gateway vérifie que le projet demandé correspond exactement au chemin de dépôt validé au lancement. Un changement de dépôt renouvelle ce contrôle. Cette vérification locale ne résiste pas à une modification malveillante du VSIX par son propriétaire.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-023 — Identité utilisateur pour Git et MR

**Origine :** évolution utilisateur du 29 octobre 2026.

Git distant utilise le credential helper ou l’agent SSH du développeur. Une MR utilise un token utilisateur déjà disponible via l’IDE ou Git Credential Manager ; en son absence l’API MR est indisponible, sans substitution de compte de service. Un accès SSH seul permet Git mais ne fournit pas automatiquement un token API GitLab.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-024 — Déconnexion et annulation contextuelle

**Origine :** évolution utilisateur du 29 octobre 2026.

La déconnexion, fermeture de workspace ou désactivation annule les travaux possédés et arrête le gateway associé. Aucun credential Git utilisateur n’est effacé. La perte du canal parent ferme aussi le gateway. Un changement de workspace exige un nouveau contrôle Git.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

<a id="section-4"></a>

## 4. Ségrégation, intégrité et confidentialité — DEVAI-025 à DEVAI-044

### DEVAI-025 — Identité canonique du projet

**Origine historique (PDF 1.0) :** PDF p. 6–7. **Responsable :** moteur / gateway.

L'autorisation DOIT utiliser le couple instance GitLab normalisée et `projectId` numérique vérifié auprès de cette instance. Le nom du dossier, le slug ou un remote fourni par le client ne suffit pas. Les URL SSH et HTTPS sont résolues par un adaptateur borné aux instances administrées ; les redirections et hôtes inattendus sont refusés.

**Recette :** deux instances ayant le même ID numérique restent distinctes ; renommer un projet ne transfère pas les droits à un autre ID.

### DEVAI-026 — Refus par défaut et droits par action

**Origine historique (PDF 1.0) :** PDF p. 6–7. **Responsable :** politique serveur.

Le défaut DOIT être `DENY`. Une autorisation exige : utilisateur actif, accès au projet, politique valide, `aiEnabled=true`, action explicitement permise, modèle autorisé, contexte conforme et quota disponible. Les actions de base sont `CHAT`, `CODE_GENERATION`, `SONAR_REMEDIATION`, `XRAY_REMEDIATION`, `MR_DESCRIPTION`, `CHANGELOG`, `RELEASE_NOTES` ; `AGENT_RUN` est ajoutée explicitement pour éviter qu'un droit de chat autorise tous les agents. Une action inconnue est refusée.

**Recette :** une politique permettant uniquement `CHAT` refuse un agent et une remédiation même si le fournisseur IA est identique.

### DEVAI-027 — Matrice IA / non-IA

**Origine historique (PDF 1.0) :** PDF p. 3, 6. **Responsable :** politique / extension.

Le système DOIT appliquer la matrice ci-dessous indépendamment du grisement visuel. « Selon droits » inclut droits de projet, santé du service et prérequis ; il ne signifie pas accès universel.

| Fonction | IA autorisée pour l'action | IA interdite |
|---|---|---|
| Catalogue / génération de starter | Selon droits | Selon droits |
| Chat / génération IA / agents | Selon permissions distinctes | Refus |
| Scan Sonar / lecture de rapport | Selon droits | Selon droits |
| Remédiation Sonar IA | Selon permissions | Refus |
| Scan Xray / lecture de rapport | Selon droits | Selon droits |
| Remédiation Xray IA | Selon permissions | Refus |
| Git / MR manuelle | Selon droits GitLab | Selon droits GitLab |
| Description de MR par IA | Selon permissions | Refus |

**Recette :** chaque cellule est couverte par au moins un test positif ou négatif.

### DEVAI-028 — Lecture officielle et atomique des politiques

**Origine historique (PDF 1.0) :** PDF p. 7 ; Conception. **Responsable :** gateway / configuration.

La gateway DOIT lire politique et signature depuis un commit immuable du dépôt de gouvernance attendu, via GitLab Repository Files API. La référence protégée est résolue une fois en SHA ; tous les fichiers d'un bundle proviennent de ce SHA. Le client ne choisit ni dépôt, ni chemin, ni référence de politique. Référence : [GitLab Repository Files API](https://docs.gitlab.com/api/repository_files/).

```mermaid
sequenceDiagram
    participant G as Gateway
    participant L as GitLab gouvernance
    participant V as Vérificateur
    G->>L: Résoudre la référence protégée en commit SHA
    L-->>G: SHA immuable
    G->>L: Lire politique et signature au même SHA
    L-->>G: Octets et métadonnées
    G->>V: Vérifier et valider le bundle
    V-->>G: Snapshot valide ou refus
    G->>G: Remplacer atomiquement le snapshot actif
```

**Recette :** une modification de branche entre deux téléchargements ne produit jamais un mélange de versions.

### DEVAI-029 — Signature Ed25519 sur octets exacts

**Origine historique (PDF 1.0) :** PDF p. 7–9 ; Conception. **Responsable :** sécurité Java.

La signature détachée Ed25519 DOIT couvrir exactement les octets UTF-8 distribués, sans reformatage JSON préalable. L'enveloppe contient `keyId`, algorithme fixé à `Ed25519`, signature Base64 et empreinte SHA-256 informative. La clé est recherchée dans le magasin de confiance administré ; un fichier de politique ne peut fournir sa propre clé de confiance. L'intégrité est vérifiée avant le parsing métier ; taille et formats sont bornés avant toute allocation importante.

**Recette :** changer un octet, utiliser une clé inconnue, une signature tronquée ou un algorithme différent conduit au refus ; le hash seul n'autorise rien.

### DEVAI-030 — Schéma, dates et sémantique

**Origine historique (PDF 1.0) :** PDF p. 6–9 ; Conception. **Responsable :** sécurité / configuration.

Le parseur DOIT rejeter champs critiques inconnus, doublons de clés JSON, IDs dupliqués, version de schéma inconnue, quota négatif, dates invalides et règle contradictoire. `issuedAt < expiresAt`, une émission future hors tolérance de 60 secondes est refusée, et `now >= expiresAt` interdit l'IA. Les durées maximales de validité sont gouvernées ; la base proposée est 24 heures. L'heure serveur de référence est UTC et surveillée.

**Recette :** les tests couvrent précisément les bornes de validité, les doublons et un schéma de version future.

### DEVAI-031 — Anti-rejeu et rotation des clés

**Origine historique (PDF 1.0) :** PDF p. 7 ; Conception. **Responsable :** gateway / exploitation.

La gateway DOIT persister le plus haut `policyVersion` accepté par périmètre de gouvernance, y compris après redémarrage. Une version inférieure est refusée ; une même version avec une empreinte différente est refusée. Un rollback métier se publie sous une version plus élevée. La rotation utilise une période de coexistence bornée et un canal d'administration de confiance ; une clé compromise est révoquée indépendamment de la signature d'un ancien bundle.

```mermaid
sequenceDiagram
    participant A as Administrateur
    participant CI as CI gouvernance
    participant G as Gateway
    participant DB as Registre de versions
    A->>CI: Politique relue, version supérieure
    CI->>CI: Valider et signer via coffre
    CI-->>G: Bundle publié sur GitLab
    G->>DB: Comparer version et empreinte atomiquement
    alt Version acceptée
      DB-->>G: Nouvelle borne persistée
    else Ancien bundle ou collision
      DB-->>G: Refus et audit
    end
```

**Recette :** rejouer une politique précédemment valide après redémarrage ne rétablit aucun droit retiré.

### DEVAI-032 — Cache, révocation et mode dégradé

**Origine historique (PDF 1.0) :** PDF p. 7, 12 ; Conception. **Responsable :** gateway.

Le cache DOIT contenir uniquement des snapshots signés valides. Une décision réévalue droits et expiration à chaque requête ; le cache de droits a une fraîcheur maximale proposée de 60 secondes. Une notification authentifiée de révocation invalide immédiatement les entrées concernées, avec rafraîchissement périodique en secours. En panne de gouvernance, seul un cache encore valide et suffisamment frais peut servir ; passé ce délai, aucun nouvel appel IA. Ce délai borné est la latence maximale de révocation annoncée, pas une révocation instantanée universelle.

**Recette :** au-delà de 60 secondes sans revalidation, l'IA est refusée ; une révocation reçue est effective à la prochaine vérification sans attendre l'expiration générale.

### DEVAI-033 — Cycle de décision complet

**Origine historique (PDF 1.0) :** PDF p. 7. **Responsable :** moteur / gateway.

L'ouverture du workspace, un changement de remote, branche, confiance ou identité, une expiration et chaque requête IA DOIVENT déclencher les vérifications adaptées. Le résultat expose capacités, version de politique, durée de validité et motif de refus. Un cache UI n'est jamais un titre d'accès.

```mermaid
sequenceDiagram
    participant E as Extension
    participant J as Moteur
    participant G as Gateway
    participant L as GitLab
    E->>J: Workspace ouvert ou contexte modifié
    J->>G: Résoudre projet et demander capacités
    G->>L: Vérifier identité projet et droits utilisateur
    G->>G: Évaluer snapshot signé et limites
    G-->>J: Capacités et échéance
    J-->>E: Mettre à jour les cinq vues
    E->>J: Action IA
    J->>G: Requête authentifiée et contexte
    G->>G: Réévaluer avant appel fournisseur
```

**Recette :** un changement de remote entre affichage des capacités et envoi ne permet pas d'utiliser l'autorisation précédente.

### DEVAI-034 — Intégrité des ressources embarquées

**Origine historique (PDF 1.0) :** PDF p. 7. **Responsable :** moteur / packaging.

Le VSIX et les ressources statiques sensibles DOIVENT être accompagnés d'un manifeste signé avec empreintes SHA-256. Le moteur vérifie les ressources au démarrage et avant utilisation sensible. Une altération bloque la ressource et produit un diagnostic ; elle ne peut être résolue par un simple attribut lecture seule ou une clé AES livrée dans le même paquet.

**Recette :** une modification du catalogue embarqué est détectée ; l'absence de vérification côté client ne contourne pas les règles serveur.

### DEVAI-035 — Secrets techniques embarqués et limites acceptées

**Origine :** évolution utilisateur du 29 octobre 2026.

Les comptes de service et clés sont configurés par l’organisation au build, embarqués dans le VSIX privé et non éditables dans l’interface. Ils sont extractibles par le propriétaire du poste : cette limite a été explicitement acceptée. Les logs et la webview masquent les secrets et formes d’autorisation. Les exemples, sources Git et artefacts CI génériques contiennent uniquement des placeholders. Une rotation exige un nouveau build privé.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-036 — TLS et destinations autorisées

**Origine historique (PDF 1.0) :** PDF p. 4, 11 ; Conception. **Responsable :** réseau / gateway / moteur.

Les appels distants DOIVENT vérifier certificats et noms d'hôte, utiliser le truststore organisationnel et limiter les destinations à celles administrées. Les redirections vers une autre origine, URL arbitraires de rapports, accès aux métadonnées cloud et résolutions privées inattendues sont bloqués côté connecteurs. Aucun mode de production ne désactive TLS pour contourner un certificat invalide.

**Recette :** certificat non approuvé et URL de rapport dirigée vers un service interne non autorisé sont refusés.

### DEVAI-037 — Filtrage des secrets et exclusions de contexte

**Origine historique (PDF 1.0) :** PDF p. 5, 9, 12 ; Conception. **Responsable :** contexte Java / gateway.

Le contexte DOIT exclure fichiers de secrets, credentials, clés privées, historiques sensibles, répertoires générés et motifs administrés. Le filtrage combine chemins, types, contenu et limites de taille, puis s'exécute à nouveau côté gateway sur les données reçues. Une exclusion organisationnelle ne peut être levée localement. La détection n'est pas présentée comme exhaustive : un faux négatif reste un risque à réduire par politique et contrôle d'accès.

**Recette :** `.env`, clé privée de test, token synthétique et secret inclus dans une sélection ou un rapport sont bloqués ou masqués avant appel au fournisseur ; les valeurs ne sont pas loggées.

### DEVAI-038 — Protection contre les instructions injectées

**Origine historique (PDF 1.0) :** PDF p. 9, 12 ; Conception. **Responsable :** IA / agents.

Les fichiers, commentaires, rapports Sonar/Xray, sorties de commandes et messages utilisateur DOIVENT être traités comme données non fiables. Ils ne peuvent modifier instructions système signées, permissions, endpoints ou outils autorisés. Les sorties du modèle passent par validation de schéma et d'autorisation avant tout effet ; une consigne dans un fichier ne déclenche pas de transfert, installation ou exécution.

**Recette :** un rapport demandant d'ignorer les règles et d'envoyer des credentials n'obtient ni contexte supplémentaire ni capacité réseau.

### DEVAI-039 — Autorisation uniforme de tous les chemins IA

**Origine historique (PDF 1.0) :** PDF p. 12. **Responsable :** gateway.

Chaque chemin vers le fournisseur IA DOIT passer par le même point de décision, y compris chat, agent, remédiations, descriptions de MR et tentatives de reprise. Les routes métiers déterminent elles-mêmes l'action ; le client ne peut étiqueter une remédiation comme simple chat pour obtenir des privilèges supplémentaires. Chaque étape d'agent sensible réévalue les droits.

**Recette :** des tests de contrat prouvent l'absence d'appel fournisseur pour toutes les routes lorsque l'action correspondante est interdite.

### DEVAI-040 — Provenance du contexte local

**Origine historique (PDF 1.0) :** PDF p. 7, 12 ; Conception. **Responsable :** moteur de contexte.

En mode `LOCAL_GOVERNED`, chaque fichier DOIT être rattaché au dépôt autorisé par chemin réel, racine, état Git, commit de base et empreinte. Traversées de chemin, liens symboliques sortants, junctions Windows et dépôts imbriqués non autorisés sont refusés. Les modifications non commitées sont identifiées comme telles et soumises à la politique de transfert. Le mode ne prouve pas cryptographiquement l'origine d'un contenu arbitraire sur un poste compromis.

**Recette :** une sélection provenant d'une racine interdite ou d'un lien vers cette racine ne rejoint pas le contexte d'un projet autorisé.

### DEVAI-041 — Mode renforcé de contexte serveur

**Origine :** Conception pour traiter la limite du PDF p. 7. **Responsable :** gateway / sécurité.

Une politique DOIT pouvoir imposer `SERVER_VERIFIED`. Dans ce mode, le client transmet des références de fichiers et commit ; la gateway récupère elle-même les blobs autorisés dans GitLab, vérifie les droits et n'accepte pas de source locale arbitraire ou non commitée. Les prompts signés et paramètres typés restent possibles. Le texte libre demeure filtré, mais s'il faut garantir qu'aucune source locale arbitraire n'est transférée, la politique désactive aussi le texte libre et limite les entrées aux prompts approuvés et références serveur. Le produit doit afficher cette restriction explicitement.

```mermaid
sequenceDiagram
    participant J as Moteur local
    participant G as Gateway
    participant L as GitLab
    participant M as IA
    J->>G: Prompt approuvé, projectId, commit, chemins
    G->>G: Vérifier politique SERVER_VERIFIED et paramètres
    G->>L: Lire les blobs au commit autorisé
    L-->>G: Sources et métadonnées
    G->>G: Filtrer et assembler le contexte
    G->>M: Contexte contrôlé et instructions signées
    M-->>G: Proposition
```

**Recette :** des octets de code injectés dans la requête client sont refusés dans ce mode ; un commit hors périmètre lisible est rejeté.

### DEVAI-042 — Prévisualisation et consentement au transfert

**Origine historique (PDF 1.0) :** PDF p. 3, 5 ; Conception. **Responsable :** extension / contexte.

Avant un transfert de sources, l'interface DOIT montrer fichiers et extraits retenus, exclusions, mode de provenance, alias de modèle et estimation de taille. Le consentement de session est borné au projet et à la politique ; élargir les fichiers ou les destinations exige une nouvelle décision. Une demande de transfert interdite ne peut être autorisée par consentement utilisateur.

**Recette :** l'utilisateur peut retirer un fichier facultatif ; les données réellement envoyées correspondent au manifeste de contexte affiché et audité par empreinte.

### DEVAI-043 — Traçabilité des décisions de sécurité

**Origine historique (PDF 1.0) :** PDF p. 8–10 ; Conception. **Responsable :** audit.

Chaque autorisation ou refus sensible DOIT produire un événement corrélé contenant sujet pseudonymisé, projet, action, version et empreinte de politique, motif, instant UTC et résultat. Le contenu brut des sources, prompts utilisateur et tokens n'est pas journalisé par défaut. Les événements sont protégés contre modification par les rôles applicatifs ordinaires.

**Recette :** un auditeur autorisé peut expliquer un refus à partir de son identifiant de corrélation sans accéder au code source envoyé.

### DEVAI-044 — Validation et publication de gouvernance

**Origine historique (PDF 1.0) :** PDF p. 7–9 ; Conception. **Responsable :** CI gouvernance.

La chaîne d'administration DOIT tester schémas, collisions de versions, droits, prompts, catalogues et signatures avant publication. Les clés privées de signature restent côté coffre/CI. Une politique d'exemple ne peut être chargée comme production sans configuration explicite du dépôt officiel. Les changements sont accompagnés d'un historique et d'une procédure de rollback à numéro de version croissant.

**Recette :** un bundle invalide ne peut être publié par le pipeline ; une restauration de droits passe par une nouvelle version et reste auditée.

<a id="section-5"></a>

## 5. Project Initializer et starters — DEVAI-045 à DEVAI-062

### DEVAI-045 — Parcours de création

**Origine historique (PDF 1.0) :** PDF p. 4–5 ; Utilisateur. **Responsable :** starter / extension.

L'assistant DOIT proposer stack, variante, version autorisée, nom, coordonnées propres à la stack et destination. Il présente prérequis, origine du starter, options résolues, fichiers créés, commandes et éventuels accès réseau avant exécution. Il fonctionne sans IA. La création d'un dépôt distant est une action distincte explicitement choisie.

```mermaid
sequenceDiagram
    actor D as Développeur
    participant E as Initializer
    participant J as Moteur starter
    participant G as Catalogue signé
    participant F as Destination
    E->>G: Lire les starters autorisés
    G-->>E: Standards et surcharges
    D->>E: Choisir stack, options et destination
    E->>J: Préparer un plan
    J-->>E: Plan, prérequis et effets de bord
    D->>E: Valider le plan
    E->>J: Générer en zone temporaire
    J->>J: Vérifier contenu et tests du starter
    J->>F: Publier sans écraser les fichiers existants
    J-->>E: Résultat et provenance
```

**Recette :** les quatre familles sont créables depuis un dossier vide ; une annulation avant validation ne crée aucun projet cible.

### DEVAI-046 — Catalogue de starters standards

**Origine :** Utilisateur ; Conception. **Responsable :** starter / configuration.

La première version DOIT fonctionner avec un catalogue standard signé distribué avec ODS, sans exiger de templates organisationnels préexistants. Ses fournisseurs sont Spring Initializr, Angular CLI, un squelette Python minimal fondé sur les standards PyPA et les templates du SDK .NET. Le catalogue indique ID, version, stack, fournisseur, prérequis, paramètres, sources, empreintes et commandes de validation.

**Recette :** avec le catalogue organisationnel absent mais le catalogue standard autorisé, la création reste possible ; l'absence de réseau ou de SDK produit une erreur précise.

### DEVAI-047 — Abstraction des fournisseurs de starter

**Origine :** Utilisateur ; Conception. **Responsable :** module starter.

Le moteur DOIT offrir un contrat de fournisseur pour valider les paramètres, préparer un plan, matérialiser les fichiers et vérifier le résultat. Les implémentations standards et organisationnelles partagent ce contrat. Le manifeste référence un fournisseur connu et une source autorisée ; il ne peut injecter une classe Java, du code exécutable ou une commande shell arbitraire. Ajouter un nouveau type de fournisseur requiert un développement revu ; substituer un template de type existant n'en requiert pas.

```mermaid
sequenceDiagram
    participant UI as Initializer
    participant R as Résolveur de catalogue
    participant P as Fournisseur de starter
    participant V as Validateur
    UI->>R: Résoudre starterId et version
    R-->>UI: Descripteur signé effectif
    UI->>P: Préparer puis matérialiser le plan validé
    P->>V: Fichiers et métadonnées de provenance
    V-->>UI: Génération valide ou rapport d'échec
```

**Recette :** substituer un template GitLab signé à un starter standard ne modifie pas le code de l'extension.

### DEVAI-048 — Starter Java Spring Boot avec Maven

**Origine historique (PDF 1.0) :** PDF p. 5, 11 ; Utilisateur. **Responsable :** fournisseur Java.

Le starter Java DOIT utiliser Spring Initializr, service public approuvé ou instance miroir, avec projet Maven, Java 21, coordonnées validées et version Spring Boot autorisée. Spring Initializr est le mécanisme standard retenu : il ne faut pas inventer un « archetype Maven officiel Spring Boot ». Un archétype Maven interne réel pourra être ajouté comme fournisseur organisationnel si ses coordonnées et sa provenance sont vérifiées. Les métadonnées d'Initializr sont consultées pour vérifier versions et dépendances. Références : [Spring Initializr](https://docs.spring.io/initializr/docs/current/reference/html/), [compatibilité Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html).

La sortie DOIT contenir `pom.xml`, wrapper Maven, classe d'entrée documentée, configuration sans secret, tests, `.gitignore` et README. Les dépendances sont sélectionnées explicitement ; aucun serveur web, accès base ou starter sécurité n'est ajouté sans nécessité du profil choisi. Les templates générés sont complétés par les commentaires et références exigés en DEVAI-137 et DEVAI-138.

**Recette :** le projet généré passe le build Maven complet ; aucune configuration Gradle n'est produite et les versions effectivement utilisées sont inscrites dans la provenance.

### DEVAI-049 — Starter Angular standard

**Origine historique (PDF 1.0) :** PDF p. 5 ; Utilisateur. **Responsable :** fournisseur Angular.

Le starter Angular DOIT utiliser une version fixée et autorisée de la CLI officielle `@angular/cli` et la commande `ng new`, avec options explicites et mode non interactif. Le manifeste fixe les compatibilités Node, TypeScript et Angular. Le générateur conserve tests, configuration de build, lockfile npm et README ; les choix routing, styles et architecture de composants sont exposés lorsqu'ils sont pris en charge. Les schematics organisationnels restent une surcharge ultérieure. Référence : [Angular CLI — ng new](https://angular.dev/cli/new).

**Recette :** installation depuis le lockfile, compilation et tests non interactifs du starter réussissent sur les runtimes qualifiés ; aucune commande `ng lint` n'est annoncée si aucun target de lint n'a été configuré.

### DEVAI-050 — Starter Python conforme aux standards

**Origine historique (PDF 1.0) :** PDF p. 5 ; Utilisateur ; Conception. **Responsable :** fournisseur Python.

Python n'ayant pas de générateur universel officiel équivalent à Initializr, ODS DOIT fournir un template minimal versionné conforme aux recommandations PyPA : `pyproject.toml`, package sous `src/`, tests, README et environnement virtuel isolé. Le backend de build, Python, outils de test et lint sont sélectionnés et verrouillés dans le catalogue. Le profil de base est un package Python ; un framework web reste une variante explicitement choisie. Référence : [Packaging Python Projects — PyPA](https://packaging.python.org/en/latest/tutorials/packaging-projects/).

Le profil initial utilise `venv`, `pip`, un backend standard tel que setuptools, `build`, pytest et un outil de lint qualifié. Les versions et contraintes de développement sont générées ; aucune installation globale ni mise à jour générale de pip n'est automatique.

**Recette :** le wheel est construit, installé dans un second environnement vierge et importé ; les tests passent sans dépendre du dossier source courant.

### DEVAI-051 — Maintien du starter .NET

**Origine historique (PDF 1.0) :** PDF p. 3, 5, 11 ; Conception. **Responsable :** fournisseur .NET.

Le starter .NET DOIT utiliser les templates présents dans un SDK autorisé, via `dotnet new`, notamment un profil ASP.NET Core et un projet de tests compatible. Le SDK est fixé dans `global.json` ; les sources NuGet et options de restauration sont gouvernées. Le format de solution généré suit le SDK qualifié sans supposer une extension unique. Référence : [dotnet new](https://learn.microsoft.com/en-us/dotnet/core/tools/dotnet-new).

**Recette :** restauration, build, tests et démarrage minimal du profil ASP.NET Core passent ; les commentaires XML de documentation contiennent les références d'exigences applicables.

### DEVAI-052 — Priorité des surcharges organisationnelles

**Origine :** Utilisateur ; Conception. **Responsable :** catalogue / gouvernance.

La résolution DOIT appliquer : catalogue organisationnel valide et autorisé pour le périmètre, puis catalogue standard si la politique autorise ce repli. Une surcharge peut `REPLACE` le starter ou appliquer un `OVERLAY` versionné. Elle ne peut affaiblir les contraintes de sécurité, supprimer les tests obligatoires ni réactiver un fournisseur interdit. Une version organisationnelle invalide ne provoque pas un repli silencieux si la politique la rend obligatoire.

```mermaid
sequenceDiagram
    participant R as Résolveur
    participant O as Catalogue organisationnel
    participant S as Catalogue standard
    R->>O: Rechercher une surcharge autorisée
    alt Surcharge valide
      O-->>R: REPLACE ou OVERLAY et version
    else Absente et repli permis
      R->>S: Résoudre la version standard autorisée
      S-->>R: Descripteur standard
    else Surcharge requise mais invalide
      R->>R: Refuser la génération
    end
```

**Recette :** publier une surcharge change uniquement les générations futures ; les projets existants ne sont pas réécrits.

### DEVAI-053 — Overlay déterministe et conflits

**Origine :** Utilisateur ; Conception. **Responsable :** starter.

Un overlay DOIT déclarer sa version de base exacte, fichiers ajoutés ou remplacés et règles de fusion structurées. Les suppressions et remplacements sensibles sont explicités dans le plan. Les conflits bloquent la génération sans laisser un résultat partiel. Une fusion textuelle aveugle de `pom.xml`, JSON ou YAML n'est pas acceptable ; les formats sont parsés et revalidés.

**Recette :** deux overlays modifiant la même propriété incompatiblement produisent un conflit lisible ; réexécuter la même configuration produit les mêmes fichiers hors métadonnées explicitement non déterministes.

### DEVAI-054 — Sources, archives et chaîne d'approvisionnement

**Origine historique (PDF 1.0) :** PDF p. 5, 7 ; Conception. **Responsable :** starter / sécurité.

Le téléchargement DOIT vérifier origine autorisée, signature de manifeste, empreinte d'archive, taille compressée et taille extraite. L'extraction refuse chemins absolus, `..`, liens sortants, collisions de casse et bombes de décompression. Un template Git est fixé par commit immuable. Les scripts fournis par le template sont inertes tant qu'ils ne figurent pas dans le plan autorisé.

**Recette :** une archive synthétique avec traversée de chemin ne crée aucun fichier hors de la zone temporaire ; une empreinte incorrecte bloque la génération.

### DEVAI-055 — Prévalidation des paramètres

**Origine :** Conception. **Responsable :** starter.

Les noms de projet, packages Java/Python, coordonnées Maven, noms npm et chemins DOIVENT être validés selon la stack. Les arguments sont transmis sous forme de liste, sans interpolation dans un shell. La destination réelle est vérifiée avant et après résolution de liens. Une destination non vide nécessite une stratégie de fusion explicitement prise en charge ; par défaut la génération y est refusée.

**Recette :** caractères de commande, chemins réservés Windows, séparateurs malveillants et nom npm invalide sont rejetés avant lancement du générateur.

### DEVAI-056 — Génération transactionnelle

**Origine :** Conception. **Responsable :** starter.

Le projet DOIT être généré dans une zone temporaire contrôlée, vérifié puis déplacé vers une destination toujours libre, sur le même volume lorsque possible. Une collision apparue pendant la génération provoque un arrêt. Le nettoyage ne supprime que les fichiers temporaires appartenant à l'opération, jamais des fichiers utilisateur ou un artefact non sauvegardé.

**Recette :** un arrêt du générateur et une collision tardive préservent intégralement le répertoire utilisateur.

### DEVAI-057 — Registres et dépendances

**Origine historique (PDF 1.0) :** PDF p. 5 ; Utilisateur ; Conception. **Responsable :** starter / configuration.

Les starters standards DOIVENT pouvoir utiliser les registres publics approuvés lorsque l'organisation n'a pas de miroir. La configuration organisationnelle peut imposer Maven, npm, PyPI ou NuGet internes sans secret embarqué. Les URLs sont des paramètres administrés ; les credentials sont fournis par environnement, coffre ou mécanisme utilisateur prévu. Un accès public n'est pas utilisé en secours si le miroir obligatoire est indisponible.

**Recette :** le même fournisseur génère un projet avec registre standard ou interne ; le diff ne contient aucun identifiant réel.

### DEVAI-058 — Livrables de chaque starter

**Origine historique (PDF 1.0) :** PDF p. 5 ; Utilisateur. **Responsable :** starter.

Chaque starter DOIT produire code minimal utile, tests, `.gitignore`, README, configuration de qualité et de sécurité, références de versions et pipeline adapté au fournisseur Git choisi. La CI GitLab est la valeur métier par défaut pour les projets ODS ; une variante GitHub explicitement configurée peut être fournie. La documentation donne les commandes réellement disponibles et les prérequis, sans chemins propres au poste.

**Recette :** un second développeur peut construire et tester le projet depuis les seuls fichiers générés et les runtimes indiqués.

### DEVAI-059 — Provenance et reproductibilité des starters

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** starter.

Chaque génération DOIT créer `.ods/starter-lock.json` contenant ID/version du starter, fournisseur/version, commit ou empreinte source, version du catalogue, overlays, options résolues et exigences de génération applicables. Ce fichier ne contient ni secret, ni chemin absolu, ni identité privée. Les archives récupérées d'un service de génération sont conservées par empreinte dans un cache autorisé pour permettre leur reproduction lorsque le service amont change.

**Recette :** le rapport de génération permet de retrouver exactement les entrées et de vérifier que le résultat n'utilise pas une version implicite récente.

### DEVAI-060 — Validation des projets générés

**Origine :** Utilisateur ; Conception. **Responsable :** starter / CI.

Les commandes de build, tests et smoke tests DOIVENT être définies par profil et compatibles avec les outils générés. Elles sont présentées avant exécution. Une création de fichiers réussie et une validation échouée sont deux états distincts ; l'assistant ne prétend pas que le projet fonctionne si un SDK, réseau ou serveur manque.

**Recette :** les quatre profils standards passent leur pipeline dans des environnements propres ; une dépendance indisponible conserve le projet et affiche `VALIDATION_FAILED` ou `NON_VÉRIFIABLE` selon la cause.

### DEVAI-061 — Gestion du cache et absence de réseau

**Origine :** Conception. **Responsable :** starter / configuration.

Le mode hors ligne DOIT autoriser uniquement les bundles déjà vérifiés dont la politique autorise encore l'usage et dont les outils/dépendances nécessaires sont disponibles. Les signatures et empreintes restent vérifiées. Un catalogue périmé ou une dépendance absente n'est pas téléchargé depuis une destination improvisée.

**Recette :** hors ligne, un starter entièrement en cache fonctionne ou explique précisément les éléments manquants ; l'IA distante reste indisponible.

### DEVAI-062 — Évolution des starters existants

**Origine :** Utilisateur ; Conception. **Responsable :** produit / starter.

La substitution des starters organisationnels DOIT s'appliquer aux nouvelles créations. Une migration de projet existant reste une opération explicite, distincte de l'initialisation, qui peut être déclarée hors périmètre V1. Le fichier de provenance initiale reste conservé ; aucun lancement de VS Code ne réécrit automatiquement les projets à la dernière version de template.

**Recette :** mettre à jour le catalogue ne modifie aucun fichier d'un projet déjà ouvert.

<a id="section-6"></a>

## 6. IA, prompts, agents et consommation — DEVAI-063 à DEVAI-083

### DEVAI-063 — Conversation contextualisée

**Origine historique (PDF 1.0) :** PDF p. 5. **Responsable :** IA / extension.

L'onglet IA DOIT permettre question, explication, génération, revue et refactoring sur un contexte autorisé. Il distingue requête en préparation, envoyée, réponse en cours, terminée, annulée et échouée. Le texte libre ne remplace pas le système organisationnel ; il peut être désactivé par la politique renforcée de contexte. L'absence d'autorisation affiche sa cause et empêche tous les envois.

```mermaid
sequenceDiagram
    actor D as Développeur
    participant E as Chat
    participant J as Contexte Java
    participant G as Gateway
    participant M as Fournisseur IA
    D->>E: Question ou prompt approuvé
    E->>J: Préparer contexte et estimation
    J-->>E: Aperçu filtré
    D->>E: Valider le transfert autorisé
    E->>J: Envoyer
    J->>G: Action, projet et contexte
    G->>G: Autoriser et réserver le quota
    G->>M: Instructions signées et données
    M-->>G: Réponse et usage
    G->>G: Réconcilier usage et auditer
    G-->>E: Via moteur - texte, statut et consommation
```

**Recette :** une session conserve l'ordre des échanges et ne réutilise pas silencieusement le contexte d'un autre projet.

### DEVAI-064 — Modèle par alias administré

**Origine historique (PDF 1.0) :** PDF p. 5–6 ; Utilisateur. **Responsable :** gateway / connecteur IA.

Le client DOIT demander un alias autorisé ou accepter celui de la politique. La gateway résout fournisseur, modèle exact, limites, paramètres et credentials. **La liste organisationnelle initiale des fournisseurs autorisés contient exclusivement `MISTRAL`, et seuls les modèles Mistral explicitement listés sont utilisables.** La disponibilité d'un modèle chez Mistral ne vaut pas autorisation automatique. L'utilisateur ne peut saisir un endpoint, une clé personnelle ou un modèle hors catalogue dans les fonctions administrées. Le connecteur Mistral utilise une API officielle qualifiée ; les fonctionnalités preview ne sont pas supposées stables. Référence : [API Chat Mistral](https://docs.mistral.ai/api/endpoint/chat).

Le domaine DOIT définir un port de fournisseur IA indépendant de Mistral : capacités, génération/streaming, annulation si disponible et normalisation d'usage. V1 implémente le connecteur Mistral réel ; les autres fournisseurs ne sont ni installés par nécessité ni activés. Un ajout futur exige connecteur qualifié, secrets administrés, évaluation de confidentialité et activation dans un catalogue signé ainsi que dans les politiques concernées. Un nouveau connecteur peut nécessiter une nouvelle gateway, sans modifier les cinq vues ni le contrat de l'extension.

**Recette :** un modèle Mistral approuvé fonctionne ; un modèle Mistral non listé et tout fournisseur non Mistral sont refusés en configuration initiale, même via un appel direct. Changer d'alias Mistral approuvé conserve le contrat client et inscrit le modèle effectif dans l'audit. Un adaptateur factice de test démontre l'extensibilité sans être activable en production.

### DEVAI-065 — Construction bornée du contexte

**Origine historique (PDF 1.0) :** PDF p. 5, 9 ; Conception. **Responsable :** module contexte.

Le moteur DOIT sélectionner fichier actif, sélection et fichiers explicitement pertinents, puis appliquer provenance, exclusions, déduplication et budget. Il réserve une capacité de sortie et priorise les extraits au lieu de tronquer silencieusement un fichier au milieu d'une information critique. Les limites initiales proposées sont 20 fichiers, 256 Kio par fichier et 2 Mio de contexte brut, abaissées si le modèle ou la politique l'exige. L'estimation de tokens indique son caractère estimatif.

**Recette :** un dépôt volumineux ne provoque pas un envoi intégral ; la liste des éléments retirés pour budget est visible.

### DEVAI-066 — Gouvernance des prompts

**Origine historique (PDF 1.0) :** PDF p. 9. **Responsable :** prompts / gateway.

Les prompts approuvés DOIVENT inclure ID, version, actions autorisées, variables typées, instructions système, contraintes, exigences cibles et empreinte signée. Les catégories initiales sont `code-generation`, `code-review`, `sonar-remediation`, `xray-remediation` `gitlab-mr-description`, `git-changelog` et `git-release-notes`. La gateway compose les instructions officielles ; elle n'accepte pas un message `system` arbitraire envoyé par le client.

Le catalogue de prompts de tâches est distinct du socle système obligatoire : un prompt système global et les recommandations de chaque ligne de développement sont automatiquement composés selon DEVAI-161 à DEVAI-166. L'organisation peut ajouter des prompts de tâches et fournir ses vrais fichiers de règles ultérieurement, sans modifier l'extension.

**Recette :** une variable manquante, un prompt non approuvé ou un paramètre contenant une tentative de substitution système est refusé ou traité comme donnée.

### DEVAI-067 — Sessions et historique

**Origine historique (PDF 1.0) :** PDF p. 10 ; Conception. **Responsable :** IA / persistance.

Une session DOIT être liée au sujet et au projet, avec ID opaque, timestamps et métriques. Le contenu conversationnel est en mémoire par défaut ; sa persistance nécessite une politique explicite de conservation, contrôle d'accès et effacement. Restaurer un historique ne rétablit pas les anciennes permissions ni les anciens quotas.

**Recette :** un utilisateur ne peut charger une session d'un autre sujet ; un historique ancien est réévalué avant réutilisation de son contexte.

### DEVAI-068 — Diff exploitable et validation du patch

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** IA / moteur / extension.

Les changements proposés DOIVENT être normalisés en patch portant chemin relatif, contenu de base ou empreinte de base, modifications et références d'exigences. Les chemins sortants, fichiers secrets, binaires non prévus et écritures dans les métadonnées Git sont refusés. Le diff est ouvert dans VS Code avec choix accepter, rejeter ou sélectionner les fichiers ; aucun Markdown du modèle n'est exécuté.

**Recette :** un patch contenant `../` ou une modification non annoncée de CI est bloqué avant toute écriture.

### DEVAI-069 — Application sans perte de travail

**Origine historique (PDF 1.0) :** PDF p. 5 ; règles utilisateur ; Conception. **Responsable :** moteur / extension.

Avant application, le moteur DOIT vérifier que les empreintes du workspace et des buffers correspondent à la base du patch. Un fichier modifié depuis la proposition déclenche un conflit. Le changement est appliqué via les mécanismes éditeur adaptés, avec sauvegarde récupérable du contenu préalable et conservation des buffers non enregistrés. Les règles de backup Git du dépôt sont respectées ; aucun reset destructif n'est utilisé pour « faciliter » l'application.

```mermaid
sequenceDiagram
    actor D as Développeur
    participant E as Vue diff
    participant J as Moteur
    participant W as Workspace
    D->>E: Accepter les changements sélectionnés
    E->>J: Patch et empreintes attendues
    J->>W: Relire fichiers et état des buffers
    alt Bases inchangées et droits valides
      J->>W: Préserver le contenu puis appliquer
      J-->>E: Changements appliqués, validation requise
    else Conflit ou révocation
      J-->>E: Refus sans écriture partielle
    end
```

**Recette :** une édition manuelle entre génération et acceptation est conservée ; le patch ne s'applique pas silencieusement sur une base différente.

### DEVAI-070 — Vérification après génération

**Origine historique (PDF 1.0) :** PDF p. 5 ; Utilisateur. **Responsable :** moteur / tests.

Après un patch accepté, ODS DOIT proposer et, après autorisation correspondante, exécuter le build et les tests pertinents existants. La stratégie et les commandes proviennent du projet détecté, pas d'une supposition du modèle. L'échec laisse le diff accessible, conserve les logs filtrés et marque la proposition non validée ; aucune suppression de test n'est autorisée pour obtenir un statut vert.

**Recette :** un patch qui casse un test est affiché comme échec de validation, même si le modèle affirme sa correction.

### DEVAI-071 — Registre des agents approuvés

**Origine historique (PDF 1.0) :** PDF p. 5, 9 ; Conception. **Responsable :** agents / gouvernance.

Chaque agent DOIT être défini par un manifeste signé : ID/version, prompt, outils, actions IA, catégories de fichiers, commandes typées, destinations réseau, durée, nombre d'étapes et budget. Le droit `AGENT_RUN` seul ne donne accès qu'aux agents explicitement autorisés. Les limites initiales proposées sont 10 étapes et 10 minutes, réduites par la politique applicable.

**Recette :** un agent inconnu, une version non signée et un dépassement d'étapes sont rejetés sans élargissement automatique de permissions.

### DEVAI-072 — Plan et consentement des agents

**Origine historique (PDF 1.0) :** PDF p. 9 ; Conception. **Responsable :** agents / extension.

L'agent DOIT présenter un plan distinguant lecture, écriture, exécution, installation, accès réseau et Git distant. Chaque effet sensible est soumis à l'autorisation exacte applicable, avec projet, commande/arguments, fichiers et durée. Un nouveau plan qui élargit le périmètre invalide le consentement antérieur. L'utilisateur peut arrêter l'agent et voir les actions déjà réalisées.

```mermaid
sequenceDiagram
    actor D as Développeur
    participant A as Orchestrateur agent
    participant G as Gateway
    participant T as Outil contrôlé
    A->>G: Vérifier agent, projet et budget
    G-->>A: Capacités bornées
    A-->>D: Plan et effet demandé
    D->>A: Autoriser cette action
    A->>G: Réévaluer les permissions
    G-->>A: Décision et trace
    A->>T: Arguments validés
    T-->>A: Résultat borné et filtré
    A-->>D: État et prochaine étape éventuelle
```

**Recette :** approuver un build n'autorise pas une installation supplémentaire ni un push.

### DEVAI-073 — Exécuteur d'outils borné

**Origine historique (PDF 1.0) :** PDF p. 9, 12 ; Conception. **Responsable :** moteur / runners.

L'exécuteur DOIT utiliser des arguments structurés, répertoire de travail vérifié, environnement filtré, timeout, limite de sortie et arrêt de l'arbre de processus. Les commandes autorisées sont des outils identifiés ; aucune commande shell libre du LLM n'est exécutée. Un build peut exécuter du code du projet : il nécessite un workspace approuvé et, pour les traitements centraux, un runner isolé sans credentials de déploiement et avec sorties réseau bornées. Une liste blanche de noms de commandes ne constitue pas à elle seule une sandbox.

**Recette :** une tentative de lecture hors workspace, commande non déclarée et sous-processus persistant est bloquée ou arrêtée ; l'audit décrit la limite d'isolation utilisée.

### DEVAI-074 — Résultats d'agents et reprise

**Origine :** Conception. **Responsable :** agents / jobs.

Chaque étape DOIT produire statut, artefacts, empreintes et effets réellement réalisés. La reprise après interruption repart d'un état persistant validé, réévalue l'autorisation et ne répète pas automatiquement un effet non idempotent. Les erreurs de commande sont conservées ; l'agent n'interprète pas une sortie vide comme un succès.

**Recette :** un redémarrage après création d'une branche détecte cette branche et ne rejoue pas une création ou un push sans vérification.

### DEVAI-075 — Mesures de consommation

**Origine historique (PDF 1.0) :** PDF p. 5, 10. **Responsable :** quotas / extension.

La consommation DOIT distinguer tokens d'entrée, de sortie et total, par requête, session, utilisateur et projet, ainsi que période mensuelle UTC et solde. Les métriques retournées par le fournisseur priment sur l'estimation locale. Les coûts restent séparés, avec devise, modèle et date du tarif ; une estimation monétaire n'est pas une facture. Sans métriques définitives, le statut est `ESTIMATED` ou `PENDING_RECONCILIATION`.

**Recette :** un appel avec estimation différente de l'usage fournisseur met à jour le registre sans compter deux fois la requête.

### DEVAI-076 — Réservation atomique des quotas

**Origine historique (PDF 1.0) :** PDF p. 6, 10 ; Conception. **Responsable :** gateway / PostgreSQL.

Avant tout appel, la gateway DOIT réserver atomiquement un budget conservateur d'entrée plus sortie maximale. Les plafonds projet et utilisateur applicables sont vérifiés dans la même transaction. Les appels concurrents ne peuvent dépasser le solde disponible. Après réponse, la réservation est réconciliée avec l'usage ; si le fournisseur rapporte plus que réservé, l'écart est audité et le solde peut devenir débiteur, bloquant de nouveaux appels. Une réservation interrompue n'est libérée qu'après résolution de l'incertitude.

```mermaid
sequenceDiagram
    participant G as Gateway
    participant Q as Registre de quota
    participant M as IA
    G->>Q: Réserver avec requestId unique
    alt Solde suffisant
      Q-->>G: Réservation confirmée
      G->>M: Appel borné en sortie
      M-->>G: Usage mesuré
      G->>Q: Réconcilier une seule fois
    else Quota insuffisant
      Q-->>G: Refus
      G-->>G: Aucun appel fournisseur
    end
```

**Recette :** plusieurs requêtes simultanées consomment au maximum le budget réservé ; les réservations ne sont pas remises à zéro lors d'un redémarrage.

### DEVAI-077 — Idempotence des requêtes IA

**Origine :** Conception. **Responsable :** gateway / quotas.

Les requêtes facturables DOIVENT accepter une clé d'idempotence liée à utilisateur, projet, route et hash de payload. La même clé et le même payload renvoient l'opération existante ; la même clé avec un payload différent renvoie un conflit. Un timeout après envoi fournisseur n'est pas rejoué sans preuve d'absence d'exécution ou support d'idempotence du fournisseur.

**Recette :** deux clics rapprochés avec la même clé ne produisent qu'une réservation et au plus un déclenchement fournisseur ; un état incertain reste visible.

### DEVAI-078 — Streaming et annulation IA

**Origine :** Conception. **Responsable :** gateway / extension.

La réponse DOIT pouvoir être transmise par événements serveur avec IDs croissants, statut terminal et usage final. La reconnexion relit les événements disponibles sans régénérer la réponse. L'annulation arrête au mieux la production fournisseur et les prochains outils ; les tokens déjà produits sont comptabilisés. Aucun événement final d'usage reçu n'est remplacé par zéro.

**Recette :** couper la connexion puis reprendre n'engendre pas de second appel ; l'interface indique si l'annulation du fournisseur n'a pu être confirmée.

### DEVAI-079 — Limitation de débit et concurrence

**Origine :** Conception. **Responsable :** gateway.

Les appels DOIVENT être limités par utilisateur, projet et fournisseur, indépendamment du quota mensuel. Les paramètres initiaux de qualification sont deux générations IA concurrentes par utilisateur et une file bornée par projet, modifiables administrativement. Une surcharge renvoie une erreur exploitable avec délai de reprise ; aucune file non bornée en mémoire n'est admise.

**Recette :** un client insistant ne prive pas tous les projets de capacité et n'entraîne pas de croissance mémoire illimitée.

### DEVAI-080 — Pannes et indisponibilité IA

**Origine historique (PDF 1.0) :** PDF p. 12 ; Conception. **Responsable :** connecteur IA.

Les timeouts, erreurs fournisseur, interruptions et réponses malformées DOIVENT être normalisés et audités. Les retries sont bornés aux opérations sûres ; un coupe-circuit limite les appels à un service défaillant. Aucun repli vers un modèle ou fournisseur non autorisé n'est possible. Les fonctions non IA restent utilisables selon leurs propres dépendances.

**Recette :** une panne Mistral n'empêche pas la lecture d'un rapport existant et ne transfère pas les sources à un autre fournisseur.

### DEVAI-081 — Traitement des réponses et obligations de code

**Origine :** Utilisateur ; PDF p. 5. **Responsable :** génération / qualité.

La génération DOIT demander du code compatible avec le projet et des commentaires explicatifs pour toutes classes et méthodes créées, avec références d'exigences. Le validateur de patch signale commentaires manquants, références inconnues et dépendances inventées avant acceptation. Les affirmations du modèle sur build, couverture ou compatibilité sont des hypothèses jusqu'à exécution des contrôles.

**Recette :** une classe sans référence d'exigence ne passe pas le contrôle de conformité ; un code non compilé ne porte jamais le statut « validé ».

### DEVAI-082 — Confidentialité et conservation chez le fournisseur

**Origine historique (PDF 1.0) :** PDF p. 5, 9, 13 ; Conception. **Responsable :** sécurité / exploitation.

La configuration du fournisseur DOIT documenter catégories de données transférées, localisation autorisée, conditions de conservation et fonctions activées. La solution ne peut garantir par simple configuration locale qu'un fournisseur ne conserve rien : cette propriété doit être confirmée dans le service et son cadre organisationnel. Les données de projet ne servent pas à une mémoire interprojets ODS non autorisée.

**Recette :** l'ouverture de production est bloquée si le mode de transfert de sources applicable n'est pas renseigné ; aucun cache partagé de contexte ne mélange les projets.

### DEVAI-083 — Exemples de consommation et contrôles de coût

**Origine historique (PDF 1.0) :** PDF p. 10 ; Conception. **Responsable :** quotas / UX.

L'interface DOIT expliquer l'unité et l'état des chiffres. Exemple fictif : 1 200 tokens d'entrée et 300 de sortie produisent 1 500 tokens, et non 1 500 unités monétaires. Un plafond mensuel de 100 000 et 42 500 tokens déjà débités laisse 57 500 avant réservations en cours. Les alertes de seuil sont administrables ; elles n'annulent pas rétroactivement une consommation déjà réalisée.

**Recette :** les totaux de l'écran concordent avec le registre et distinguent consommé, réservé et disponible.

<a id="section-7"></a>

## 7. Quality / SonarQube — DEVAI-084 à DEVAI-094

### DEVAI-084 — Détection du projet à analyser

**Origine historique (PDF 1.0) :** PDF p. 5. **Responsable :** qualité / moteur.

Le module DOIT détecter manifests, modules et stack pour choisir un profil Sonar compatible : scanner Maven pour Java Maven, scanner .NET pour .NET, scanner adapté pour Python et Angular. Un monorepo présente les unités analysables ; une stack ambiguë demande une sélection explicite. Une absence de scanner compatible n'est pas interprétée comme absence d'anomalies. Référence : [SonarScanner for Maven](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/scanners/sonarscanner-for-maven).

**Recette :** les quatre projets de référence sélectionnent le bon profil et un projet inconnu est signalé comme non pris en charge.

### DEVAI-085 — Analyse Sonar des sources enregistrées

**Origine :** évolution utilisateur du 29 octobre 2026.

Analyser lance un scanner réel sur les sources locales enregistrées, sans prérequis de commit ni de push. Maven, SonarScanner CLI et SonarScanner .NET sont choisis selon la stack. Seul le scanner Sonar reçoit son credential de service. Le résultat est associé au ceTaskId produit par cette exécution, à son analysisId et à l’empreinte locale. Le rapport est refusé si une autre analyse a remplacé les résultats du projet. Aucun ancien rapport n’est présenté comme une nouvelle analyse.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-086 — Paramètres de scan gouvernés

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** qualité / configuration.

Le mapping projet GitLab vers clé Sonar, organisation, branche et profil DOIT être administré ou résolu de façon validée. L'utilisateur peut choisir des options exposées par le profil, pas injecter une URL, token, exclusion de sécurité ou arguments libres. Les scanners et plugins sont versionnés ; les analyses de branches/MR suivent les capacités et licences réellement disponibles.

**Recette :** une fonction Sonar non licenciée est affichée comme indisponible, sans rapport inventé ni analyse assimilée à celle de la branche demandée.

### DEVAI-087 — Suivi asynchrone Sonar

**Origine historique (PDF 1.0) :** PDF p. 11 ; Conception. **Responsable :** qualité / jobs.

Le scan DOIT exposer ID ODS, ID de tâche fournisseur, statut, progression disponible, timestamps, commit et erreurs. La gateway attend la fin du traitement Sonar avec polling borné ou événement validé. Fin du processus scanner ne signifie pas fin de calcul du serveur. Une annulation impossible côté fournisseur est annoncée comme telle.

**Recette :** un traitement serveur encore en cours ne déclenche ni rapport final ni faux Quality Gate vert.

### DEVAI-088 — Rapport de qualité normalisé

**Origine historique (PDF 1.0) :** PDF p. 5. **Responsable :** qualité / extension.

Le rapport DOIT afficher identifiant, règle, message, type, sévérité d'origine et normalisée, fichier relatif, ligne si disponible, statut et lien validé vers Sonar. Les résultats sont paginés et filtrables. Une absence de ligne ou un fichier supprimé est traitée explicitement ; les données fournisseur ne sont pas interprétées comme HTML fiable.

**Recette :** un jeu de rapports avec pagination, ligne absente et chemins spéciaux reste navigable sans perte d'issues.

### DEVAI-089 — Quality Gate et fraîcheur

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** qualité.

L'écran DOIT distinguer succès technique du scan, état du Quality Gate et obsolescence du rapport. Le Quality Gate est `PASSED`, `FAILED` ou `UNKNOWN`, selon les données effectives. Si le commit ou l'état local diffère de la cible analysée, le rapport est marqué obsolète ou partiel pour le workspace ; un rapport ancien ne valide pas de nouvelles modifications.

**Recette :** après édition locale, le résultat reste consultable mais n'est plus présenté comme validation exacte de ce code.

### DEVAI-090 — Sélection de remédiation Sonar

**Origine historique (PDF 1.0) :** PDF p. 5. **Responsable :** qualité / IA.

Dans le parcours local 0.5.0, le clic **Corriger les issues** autorise la correction des anomalies du rapport courant sans consigne supplémentaire, sur une nouvelle branche Git dédiée. L'utilisateur choisit le nombre maximal de tentatives dans l'onglet ; cette valeur est mémorisée pour le workspace. La gateway exige `SONAR_REMEDIATION`, des droits sur le rapport et la conformité du contexte. Le rapport fournisseur est une donnée non fiable ; aucun fichier non nécessaire n'est ajouté automatiquement.

Le parcours **Augmenter la couverture** exige un objectif utilisateur et des métriques Sonar réelles. Il ajoute des tests, valide compilation/tests puis régénère les métriques jusqu'à l'objectif ou à la limite choisie. Il ne doit pas augmenter artificiellement le résultat par suppression du code mesuré ou ajout d'exclusions. Un rapport téléchargeable en PDF présente chaque constat initial avec criticité, état vérifié (corrigé, partiel, non implémenté) et explication du modèle clairement distinguée des preuves.

**Recette :** le scan et la lecture fonctionnent sans IA ; demander un correctif sur le même projet retourne un refus si `SONAR_REMEDIATION` manque.

### DEVAI-091 — Boucle de correction Sonar

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** qualité / moteur.

La remédiation locale DOIT appliquer le correctif avec checkpoint, compiler et exécuter les tests avant d'afficher le diff. En cas d'échec, la relance automatique fournit au modèle le rapport initial et les diagnostics de compilation/tests, dans la limite de tentatives choisie par l'utilisateur. Une nouvelle analyse vérifie les sources corrigées ; les issues ne sont marquées résolues qu'après ce résultat correspondant. Un arrêt ou une limite atteinte conserve les changements et indique explicitement ce qui reste non validé. En mode runner distant historique, les modifications doivent d'abord être commitées et rendues accessibles à GitLab avec autorisation du développeur ; ODS ne push pas implicitement pour rescan.

```mermaid
sequenceDiagram
    actor D as Développeur
    participant Q as Quality
    participant G as Gateway IA
    participant W as Workspace
    D->>Q: Choisir la limite et cliquer Corriger
    Q->>W: Créer une branche dédiée
    Q->>G: Remédiation autorisée et contexte filtré
    G-->>Q: Patch proposé
    Q->>W: Appliquer et lancer validations autorisées
    W-->>Q: Build et tests réels
    opt Échec et tentatives disponibles
        Q->>G: Rapport initial et diagnostics pour nouvelle correction
    end
    Q->>G: Scanner la nouvelle révision disponible
    G-->>Q: Nouveau rapport et comparaison
    Q-->>D: Rapport de correction et PDF
    Q-->>D: Diff si compilation/tests validés
```

**Recette :** une correction locale sans nouveau scan est étiquetée « validation Sonar en attente », jamais « issue résolue ».

### DEVAI-092 — Non-régression de qualité

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** qualité / tests.

La comparaison DOIT montrer issues ciblées résolues, persistantes, nouvelles et statut du gate. Elle conserve les identifiants fournisseur et les commits comparés. Une baisse du nombre d'issues due à l'exclusion d'un répertoire ou à une modification du profil qualité n'est pas automatiquement une correction du code.

**Recette :** une modification d'exclusions est signalée séparément et ne satisfait pas le critère de remédiation d'une issue.

### DEVAI-093 — Validation après correction qualité

**Origine :** évolution utilisateur du 29 octobre 2026.

Après acceptation du diff, le développeur autorise les builds et tests utiles puis une nouvelle analyse locale. Un commit ou push n’est pas requis. Aucune issue n’est annoncée corrigée sans résultat d’un nouveau scan sur les sources modifiées.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-094 — Qualification Sonar multistack

**Origine historique (PDF 1.0) :** PDF p. 12. **Responsable :** QA / intégration.

La recette DOIT couvrir au moins un projet de chaque stack avec anomalies connues, processus scanner, fin de tâche, pagination, gate et droits. Des tests contractuels sur doubles sont requis en CI ; l'intégration contre l'instance de qualification est distincte et indispensable pour déclarer le connecteur validé dans l'organisation.

**Recette :** les preuves indiquent versions scanner/serveur, projet de test, commit et résultats ; sans instance réelle le statut reste `NON VÉRIFIABLE` pour cette intégration.

<a id="section-8"></a>

## 8. Application Security / JFrog Xray — DEVAI-095 à DEVAI-105

### DEVAI-095 — Inventaire des dépendances

**Origine historique (PDF 1.0) :** PDF p. 5. **Responsable :** sécurité applicative / moteur.

Le module DOIT identifier dépendances directes et transitives, coordonnées, versions résolues, portée et chaînes de dépendance. Les fichiers de verrouillage et métadonnées de build sont utilisés lorsqu'ils existent. Une version déclarée différente de la version résolue est signalée. Les dépendances de développement sont distinguées sans être automatiquement ignorées.

**Recette :** une vulnérabilité transitive montre le chemin depuis la dépendance directe responsable.

### DEVAI-096 — SBOM local et Graph Scan Xray

**Origine :** évolution utilisateur du 29 octobre 2026.

Security génère et exporte un document CycloneDX pour Java, Angular, Python et .NET. Générer rapport Xray utilise le Graph Scan API du projet scan-sbom-xray : POST /xray/api/v1/scan/graph avec watch et scan_type=dependency, puis GET /{scan_id} avec include_vulnerabilities et include_licenses. Le watch, l’URL et l’authentification BASIC ou TOKEN proviennent du build. Aucun commit n’est requis. Voir DEVAI-172 et DEVAI-173.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-097 — Résultats de vulnérabilités

**Origine historique (PDF 1.0) :** PDF p. 5. **Responsable :** Xray / extension.

Le rapport DOIT présenter identifiant fournisseur, CVE quand disponible, sévérité, score et version de score si fournis, composant/version, chemin transitif, état et versions corrigées annoncées. Les licences et violations de politiques sont montrées si l'instance les fournit, sans les confondre avec une CVE. Une donnée absente n'est pas inventée.

**Recette :** un composant sans CVE ou sans version corrigée reste affiché avec ces absences explicites.

### DEVAI-098 — Remédiation Xray autorisée

**Origine historique (PDF 1.0) :** PDF p. 5–6. **Responsable :** Xray / IA.

La demande de correction DOIT exiger `XRAY_REMEDIATION` et limiter le contexte aux findings et manifestes nécessaires : `pom.xml`, `pyproject.toml`/requirements, projet .NET, `package.json` et locks pertinents. Les données fournisseur ne peuvent remplacer les contraintes de versions organisationnelles ni la validation métier.

Le parcours local utilise la même branche dédiée, la limite de tentatives configurable, la validation compilation/tests et le rapport PDF par constat que la remédiation Sonar. Après validation, un nouveau SBOM et un nouveau scan Xray doivent confirmer la disparition des anomalies ; le seul discours du modèle ne constitue pas une preuve de résolution.

**Recette :** la lecture Xray reste disponible pour un projet sans IA, tandis que le bouton et la route de remédiation sont refusés.

### DEVAI-099 — Sélection de versions compatibles

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** dépendances / Xray.

Une version proposée DOIT être disponible dans le registre autorisé, compatible avec runtime et contraintes déclarées, et autorisée par le catalogue. Une montée majeure nécessite une explication des incompatibilités et une décision explicite. L'absence de correction connue produit une recommandation de mitigation documentée, pas une version inventée. La mise à jour générale ou `--force` n'est pas une stratégie par défaut.

**Recette :** une version corrigée absente du registre interne n'est pas proposée comme immédiatement installable.

### DEVAI-100 — Mise à jour cohérente des manifestes et locks

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** remédiation / moteur.

Le patch DOIT mettre à jour les déclarations nécessaires et régénérer les fichiers de verrouillage avec les outils du projet après approbation. Les locks ne sont pas inventés par le LLM ni édités partiellement sans résolution réelle. Le diff expose changements directs et transitifs afin que l'utilisateur mesure la portée.

**Recette :** une installation à partir du lockfile reproduit les nouvelles versions et ne dépend pas d'une résolution implicite différente.

### DEVAI-101 — Validation de correction sécurité

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** Xray / tests.

Après acceptation du patch, le système DOIT lancer build, tests et nouvel audit autorisés, puis comparer les résultats sur les versions réellement résolues. Les contraintes de commit/push du mode runner s'appliquent comme pour Sonar. Une compilation réussie ne prouve pas la disparition d'une vulnérabilité ; seul le nouveau rapport apporte cette preuve dans le périmètre de l'outil.

```mermaid
sequenceDiagram
    actor D as Développeur
    participant S as Security
    participant W as Workspace
    participant X as Scan Xray
    S-->>D: Versions candidates et diff
    D->>S: Accepter la mise à jour
    S->>W: Appliquer puis résoudre les locks
    S->>W: Exécuter build et tests autorisés
    W-->>S: Résultats
    D->>S: Autoriser publication de la révision si nécessaire
    S->>X: Auditer la révision corrigée
    X-->>S: Disparues, persistantes et nouvelles vulnérabilités
```

**Recette :** si une vulnérabilité reste présente, le résultat indique « correction insuffisante » même si le build passe.

### DEVAI-102 — Fraîcheur et correspondance des rapports

**Origine historique (PDF 1.0) :** PDF p. 5 ; Conception. **Responsable :** Xray / persistance.

Un rapport DOIT être rattaché à son commit ou build, ses empreintes de manifestes/locks, sa date et sa configuration d'analyse. Une analyse partielle ou un échec de résolution est visible. Un rapport sans dépendances résolues ne vaut pas résultat « aucune vulnérabilité ».

**Recette :** changer un lockfile rend le rapport précédent obsolète et un échec réseau lors de la résolution ne produit pas un état vert.

### DEVAI-103 — Validation après correction sécurité

**Origine :** évolution utilisateur du 29 octobre 2026.

Après application du diff, exécuter build et tests autorisés, régénérer le SBOM local, puis lancer un nouveau Graph Scan. Une compilation réussie ne prouve pas la disparition d’une CVE. L’empreinte des sources et les versions résolues relient chaque résultat aux entrées analysées.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-104 — Capacités et erreurs JFrog

**Origine historique (PDF 1.0) :** PDF p. 5, 13 ; Conception. **Responsable :** connecteur JFrog.

Le connecteur DOIT qualifier les capacités de l'instance et distinguer outil absent, licence insuffisante, écosystème non pris en charge, accès refusé et scan échoué. L'API exacte est encapsulée par version ; aucune route proposée dans ce document n'est présentée comme route native universelle de Xray.

**Recette :** un audit non disponible sur l'instance est annoncé sans simuler une réponse Xray de production.

### DEVAI-105 — Qualification Xray multistack

**Origine historique (PDF 1.0) :** PDF p. 12. **Responsable :** QA / intégration.

La recette DOIT couvrir Java Maven, Python, .NET et Angular avec dépendances directes/transitives, résolution, vulnérabilités connues, absence de correction et registre indisponible. Les contrats utilisent des fixtures sans secrets ; l'intégration réelle indique version CLI, version serveur, capacités et preuve d'audit.

**Recette :** les écarts entre fixtures et serveur réel sont documentés ; les tests réels non exécutés ne sont pas remplacés par le statut des doubles.

<a id="section-9"></a>

## 9. GitFlow et merge requests — DEVAI-106 à DEVAI-116

### DEVAI-106 — Diagnostic Git préalable

**Origine historique (PDF 1.0) :** PDF p. 6 ; règles utilisateur. **Responsable :** GitFlow.

Avant toute mutation Git, le moteur DOIT lire dépôt réel, branche, état des fichiers, remotes, upstream, opérations en cours et éventuel HEAD détaché. Plusieurs remotes nécessitent une cible déterminée sans ambiguïté. Un dépôt absent active le parcours d'initialisation au lieu d'inventer une branche ou un remote.

**Recette :** un rebase en cours ou un HEAD détaché produit un diagnostic et empêche les opérations incompatibles.

### DEVAI-107 — Clone, fetch et pull guidés

**Origine historique (PDF 1.0) :** PDF p. 6 ; Conception. **Responsable :** GitFlow.

ODS DOIT proposer clone, fetch et pull avec identité utilisateur, affichage de la destination/remote et stratégie de pull définie par le dépôt. Le clonage refuse une destination occupée. Avant pull, les modifications locales sont préservées ; aucune stratégie merge/rebase, acceptation de clé SSH ou désactivation TLS n'est choisie silencieusement.

**Recette :** un pull conflictuel conserve le travail et affiche les fichiers à résoudre, sans reset ni nettoyage destructif.

### DEVAI-108 — Branches et protections

**Origine historique (PDF 1.0) :** PDF p. 6 ; règles utilisateur. **Responsable :** GitFlow.

Le produit DOIT appliquer les conventions administrées : `main` stable, `develop` courant, préfixes `feat/`, `fix/`, `chore/`, `docs/` lorsque ce GitFlow est retenu. Les protections réelles GitLab restent prioritaires. La création et la bascule vérifient base, collision et modifications non sauvegardées ; un projet suivant une autre convention validée n'est pas converti automatiquement.

**Recette :** un nom interdit est refusé avant Git et une branche protégée ne reçoit pas d'écriture contournant GitLab.

### DEVAI-109 — Préservation et backup

**Origine :** règles utilisateur ; Conception. **Responsable :** GitFlow / moteur.

Avant une modification de code régie par les règles du dépôt, ODS DOIT préserver l'état précédent et créer la branche `backup/<branche-source>-<version>-YYYYMMDDHHmmss`, avec `unversioned` si nécessaire. Les changements préexistants sont examinés et préservés sans écrasement ; un commit de sauvegarde n'inclut pas de secrets. Le push de backup suit la politique et l'identité utilisateur. Une obligation de sauvegarde distante non réalisable bloque l'action qui en dépend et est expliquée.

**Recette :** un état local sale peut être restauré ; aucun `reset --hard`, `clean -fdx` ou équivalent ne supprime le travail pour créer le backup.

### DEVAI-110 — Staging et commit explicites

**Origine historique (PDF 1.0) :** PDF p. 6 ; règles utilisateur. **Responsable :** GitFlow.

La vue DOIT montrer fichiers, diff, éléments staged et contrôles de secrets avant commit. Le développeur choisit le périmètre ; un agent ne fait pas `add -A` sans analyse. Le message respecte les conventions du projet. L'identité Git reste celle configurée et autorisée ; aucune identité privée n'est inventée ou remplacée globalement.

**Recette :** un fichier de credentials non ignoré empêche le commit proposé et un fichier utilisateur non sélectionné n'y entre pas.

### DEVAI-111 — Push contrôlé

**Origine historique (PDF 1.0) :** PDF p. 6 ; Conception. **Responsable :** GitFlow.

Avant push, ODS DOIT afficher remote, branche, commits et état des validations, vérifier les secrets et protections, puis utiliser les droits du développeur. Aucun force push n'est effectué par défaut. Un rejet distant reste un rejet ; la solution ne contourne ni protection ni divergence par écrasement.

**Recette :** une branche distante avancée provoque une résolution guidée ; l'historique distant n'est pas réécrit automatiquement.

### DEVAI-112 — Création de merge request

**Origine historique (PDF 1.0) :** PDF p. 6, 10. **Responsable :** GitFlow / gateway.

L'utilisateur DOIT pouvoir préparer source, cible, titre, description, tickets et reviewers selon les droits disponibles, prévisualiser puis créer une MR via son identité GitLab. Une clé d'idempotence et une recherche de MR existante évitent les doublons ; un timeout n'entraîne pas une seconde création aveugle. Référence : [GitLab Merge Requests API](https://docs.gitlab.com/api/merge_requests/).

```mermaid
sequenceDiagram
    actor D as Développeur
    participant E as GitFlow
    participant G as Gateway
    participant L as GitLab
    D->>E: Préparer la MR
    E-->>D: Aperçu source, cible, titre et description
    D->>E: Publier la MR
    E->>G: Demande et clé d'idempotence
    G->>L: Vérifier droits et MR existante
    alt Nouvelle demande autorisée
      G->>L: Créer avec identité utilisateur
      L-->>G: ID et URL de MR
    else Déjà créée
      L-->>G: MR existante
    end
    G-->>E: Résultat vérifié
```

**Recette :** deux soumissions identiques renvoient la même MR et son auteur est le développeur.

### DEVAI-113 — Description de MR assistée par IA

**Origine historique (PDF 1.0) :** PDF p. 6. **Responsable :** GitFlow / IA.

La génération DOIT exiger `MR_DESCRIPTION`, un diff borné et filtré, commits, résultats de tests réellement disponibles et rapports pertinents. La sortie propose titre, résumé, impacts, tests, tickets et points de revue, sans inventer de ticket ou test réussi. Le développeur peut modifier le texte avant publication ; la génération ne crée pas la MR.

**Recette :** si aucun test n'a été exécuté, la description indique « non exécuté » ; l'absence de droit IA laisse la rédaction manuelle possible.

### DEVAI-114 — Résolution des conflits et opérations risquées

**Origine :** règles utilisateur ; Conception. **Responsable :** GitFlow / extension.

Les conflits DOIVENT être présentés dans les outils éditeur natifs et nécessiter une résolution explicite. L'IA peut proposer une résolution si autorisée, mais elle ne choisit pas silencieusement « ours » ou « theirs ». Rebase, reset, suppression de branche et nettoyage restent hors du parcours simple V1 ou passent par une autorisation dédiée avec sauvegarde vérifiée.

**Recette :** un conflit ne déclenche aucune commande destructive ; les deux versions restent récupérables.

### DEVAI-115 — Suivi CI et merge protégé

**Origine historique (PDF 1.0) :** PDF p. 6 ; règles utilisateur. **Responsable :** GitFlow / intégration.

ODS DOIT afficher l'état CI et les protections associés au commit exact de la MR. Une validation d'un ancien commit ne valide pas le dernier push. Le merge reste soumis aux approbations GitLab et aux règles du dépôt ; une éventuelle commande de merge explicite n'est activée qu'après contrôles et consentement, sans identité de service privilégiée.

**Recette :** pousser un nouveau commit invalide l'ancien statut local de validation et empêche un merge déclaré prêt tant que les checks applicables ne sont pas à jour.

### DEVAI-116 — Traçabilité Git et indépendance de l'IA

**Origine historique (PDF 1.0) :** PDF p. 6 ; Conception. **Responsable :** GitFlow / audit.

Les opérations Git DOIVENT journaliser type, projet, branche, commit avant/après lorsque pertinent, identité logique et résultat sans credentials ni contenu complet de diff. GitFlow doit rester exploitable sans IA et sans serveur IA ; les actions Git locales peuvent fonctionner si la gateway est indisponible, sous réserve des règles locales et des droits Git distants.

**Recette :** désactiver Mistral n'empêche ni commit local ni MR manuelle avec les droits nécessaires ; les actions sensibles restent traçables.

<a id="section-10"></a>

## 10. Contrats, données et exploitation — DEVAI-117 à DEVAI-132

### DEVAI-117 — Contrat REST interne versionné

**Origine historique (PDF 1.0) :** PDF p. 10–11 ; Conception. **Responsable :** API gateway.

La gateway DOIT publier un contrat OpenAPI versionné sous `/api/v1`, avec schémas, authentification, droits, exemples, pagination, erreurs et statuts asynchrones. Chaque route vérifie l'accès aux objets référencés ; un UUID opaque n'est pas une autorisation. Les routes ci-dessous sont des **contrats ODS à développer**, distincts des API natives GitLab, Sonar, JFrog et Mistral. Les extensions incompatibles nécessitent une nouvelle version majeure de contrat.

**Recette :** les clients et tests sont validés contre le contrat ; requêtes supplémentaires inattendues et accès croisés aux ressources sont refusés.

### DEVAI-118 — Erreurs, corrélation et pagination

**Origine :** Conception. **Responsable :** API / extension.

Les erreurs DOIVENT contenir `code`, message sûr, `correlationId`, caractère réessayable et détails non sensibles. Les conventions sont : 400 requête invalide ; 401 session requise ; 403 droit refusé ; 404 ressource absente ou non révélable ; 409 conflit/idempotence ; 413 trop volumineux ; 422 contexte sémantiquement invalide ; 429 quota/débit ; 503 dépendance indisponible. Les listes sont paginées par curseur opaque, taille par défaut 50 et maximum 200.

**Recette :** aucune exception Java, token ou chemin absolu n'apparaît dans une réponse publique ; les pages ne perdent ni ne dupliquent les résultats d'un snapshot stable.

### DEVAI-119 — Modèle de jobs persistants

**Origine historique (PDF 1.0) :** PDF p. 11 ; Conception. **Responsable :** jobs / PostgreSQL.

Les scans, agents et remédiations longues DOIVENT retourner rapidement `202 Accepted` avec `jobId` et URL de suivi. États : `QUEUED`, `RUNNING`, `WAITING_APPROVAL`, `CANCEL_REQUESTED`, puis `SUCCEEDED`, `FAILED`, `CANCELLED` ou `TIMED_OUT`. Les transitions sont validées et persistées. `SUCCEEDED` décrit l'exécution du job, pas nécessairement un Quality Gate réussi ou l'absence de vulnérabilité.

```mermaid
stateDiagram-v2
    [*] --> QUEUED
    QUEUED --> RUNNING
    QUEUED --> CANCELLED
    RUNNING --> WAITING_APPROVAL
    WAITING_APPROVAL --> RUNNING
    RUNNING --> CANCEL_REQUESTED
    WAITING_APPROVAL --> CANCEL_REQUESTED
    CANCEL_REQUESTED --> CANCELLED
    CANCEL_REQUESTED --> SUCCEEDED
    CANCEL_REQUESTED --> FAILED
    CANCEL_REQUESTED --> TIMED_OUT
    RUNNING --> SUCCEEDED
    RUNNING --> FAILED
    RUNNING --> TIMED_OUT
    WAITING_APPROVAL --> TIMED_OUT
```

```mermaid
sequenceDiagram
    participant C as Client
    participant G as Gateway
    participant DB as Stockage transactionnel
    participant W as Worker
    C->>G: Créer un scan
    G->>DB: Insérer job et événement dans une transaction
    G-->>C: 202 et jobId
    W->>DB: Acquérir un bail de job
    W->>W: Exécuter l'adaptateur autorisé
    W->>DB: Enregistrer résultat et statut
    C->>G: Lire statut ou événements
    G-->>C: État persistant
```

**Recette :** un redémarrage gateway ne perd pas les jobs et une annulation tardive peut honnêtement retourner un résultat déjà terminé.

### DEVAI-120 — Concurrence, baux et reprise des jobs

**Origine :** Conception. **Responsable :** gateway / workers.

Les workers DOIVENT acquérir les jobs avec un bail et un mécanisme empêchant deux exécutants actifs d'écrire le même résultat. Un heartbeat renouvelle le bail ; les écritures vérifient le jeton de génération du bail. Après expiration, le job est réconcilié avec l'ID fournisseur avant reprise. Une livraison au moins une fois des événements est admise ; les consommateurs sont idempotents. Aucun engagement « exactly once » n'est présumé à travers les API tierces.

**Recette :** deux workers concurrents et un crash après envoi fournisseur ne créent pas un second effet non vérifié.

### DEVAI-121 — Modèle de données central

**Origine historique (PDF 1.0) :** PDF p. 4, 10–11 ; Conception. **Responsable :** persistance.

Le stockage local H2 (ou PostgreSQL pour un serveur séparé) DOIT conserver projets référencés, sujets pseudonymisés, snapshots de gouvernance, versions anti-rejeu, jobs, événements, références de rapports, sessions de métadonnées, réservations et consommations, idempotence et audit. Les relations portent systématiquement le périmètre instance/projet. Les données longues de rapport peuvent utiliser un stockage objet administré si nécessaire ; aucun stockage objet n'est supposé déjà disponible.

```mermaid
sequenceDiagram
    participant API as Service métier
    participant DB as Stockage transactionnel
    participant AUD as Lecture audit
    API->>DB: Transaction job, quota et événement associé
    DB-->>API: Commit ou rollback cohérent
    AUD->>DB: Requête filtrée par droits et projet
    DB-->>AUD: Métadonnées sans sources brutes
```

**Recette :** les contraintes empêchent une consommation sans requête et une référence de rapport liée à un autre projet ; les requêtes de lecture vérifient le périmètre.

### DEVAI-122 — Migrations et intégrité des données

**Origine :** Conception. **Responsable :** persistance / exploitation.

Les changements de schéma DOIVENT être des migrations versionnées exécutées par un outil retenu et verrouillé, avec tests depuis la version précédente. Les modifications de données destructives nécessitent une stratégie de préservation. Les migrations sont compatibles avec le mode de déploiement choisi ; un rollback binaire n'est pas réputé annuler une migration irréversible.

**Recette :** création de base vierge et montée de version sur sauvegarde de qualification passent, avec procédure de restauration documentée.

### DEVAI-123 — Rétention et suppression

**Origine historique (PDF 1.0) :** PDF p. 10 ; Conception. **Responsable :** données / exploitation.

Les catégories de données DOIVENT avoir une rétention administrée : proposition initiale de 90 jours pour audit et registre d'usage, 30 jours pour jobs/rapports, aucune conservation centrale des textes de chat par défaut. Une politique organisationnelle plus adaptée peut les remplacer. La suppression respecte les obligations applicables, les références comptables et les sauvegardes ; les versions anti-rejeu ne sont pas purgées comme un simple log.

**Recette :** une purge de qualification supprime les données arrivées à échéance sans permettre le rejeu d'une ancienne politique ni casser le calcul de quota actif.

### DEVAI-124 — Journaux, métriques et traces

**Origine historique (PDF 1.0) :** PDF p. 8–10 ; Conception. **Responsable :** observabilité.

Les composants DOIVENT produire logs structurés filtrés et corrélés, métriques de latence/erreur, files de jobs, refus de politique, état des dépendances et consommation. Les métriques n'utilisent pas un chemin de fichier ou un prompt comme label. Les endpoints d'administration sont protégés et ne sont pas exposés par défaut au poste. L'audit de sécurité est distinct du log de diagnostic échantillonné.

**Recette :** une transaction peut être suivie extension/moteur/gateway/runner avec un identifiant sans retrouver de token dans les traces.

### DEVAI-125 — Résilience des dépendances

**Origine historique (PDF 1.0) :** PDF p. 7, 12 ; Conception. **Responsable :** gateway.

Chaque connecteur DOIT définir timeouts de connexion et réponse, retry borné avec délai variable, coupe-circuit et limite de concurrence. Un retry n'est autorisé que s'il ne duplique pas un effet ou une facturation. L'indisponibilité du stockage transactionnel empêche les opérations exigeant quota/audit transactionnel ; aucune consommation IA non suivie n'est autorisée en mode dégradé.

**Recette :** perte de base bloque les nouveaux appels IA et un service tiers lent ne bloque pas tous les threads de la gateway.

### DEVAI-126 — Objectifs mesurables de performance

**Origine :** Conception. **Responsable :** performance / QA.

La qualification du mode autonome DOIT mesurer les cibles suivantes sur un poste de référence 4 cœurs / 8 Gio, un workspace et un job actif : UI utilisable en moins de 2 s au p95 hors démarrage Java ; moteur prêt en moins de 5 s au p95 ; capacités en cache en moins de 500 ms au p95 ; acceptation de job en moins de 1 s au p95. Les temps LLM/scanner sont affichés séparément et ne sont pas garantis par ces cibles. La volumétrie de référence doit être consignée avec le résultat.

**Recette :** un rapport de charge reproductible mesure p50/p95, mémoire et erreurs ; les écarts sont exposés, sans annoncer ces objectifs comme performances déjà obtenues.

### DEVAI-127 — Configuration organisationnelle compilée

**Origine :** évolution utilisateur du 29 octobre 2026.

Le profil JSON et ses fichiers env constituent les entrées administrateur au packaging. L’organisation choisit les URL, transports, credentials de service et mappings de projets. Les références explicites ${VARIABLE} sont résolues au build. Aucun éditeur de ces paramètres n’est fourni au développeur dans le VSIX. Les prérequis locaux Java/Python/scanners restent documentés et les exemples demeurent dépourvus de secrets réels.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-128 — Séparation des privilèges d'exploitation

**Origine historique (PDF 1.0) :** PDF p. 7–8 ; Conception. **Responsable :** exploitation / sécurité.

Les identités gateway, workers, migrations et administration DOIVENT disposer des seuls droits nécessaires. Les runners sont éphémères ou nettoyés de manière vérifiée, exécutés sans privilèges élevés et isolés par job/projet. Le code analysé ne reçoit pas la clé IA ni les droits d'administration GitLab. Les accès techniques sont révoqués/renouvelés indépendamment des sessions développeur.

**Recette :** un build de test malveillant ne peut joindre un secret de déploiement et les résidus d'un job ne sont pas visibles par le projet suivant.

### DEVAI-129 — Déploiement et health checks

**Origine historique (PDF 1.0) :** PDF p. 11 ; Conception. **Responsable :** exploitation.

La gateway DOIT disposer de contrôles de vie et de disponibilité séparés, d'un arrêt gracieux et d'une configuration externe. Le moteur local n'ouvre pas d'endpoint réseau par défaut. Le déploiement serveur peut utiliser JAR ou image OCI selon l'infrastructure réelle ; la recette choisit un mode de référence documenté et ne prétend pas valider tous les orchestrateurs.

```mermaid
sequenceDiagram
    participant O as Exploitant
    participant G as Gateway
    participant DB as Base
    participant W as Workers
    O->>G: Démarrer avec configuration externe
    G->>DB: Vérifier schéma et accès
    G->>G: Charger confiance et politiques
    G-->>O: Ready ou raison de non disponibilité
    O->>G: Arrêt gracieux
    G->>W: Arrêter l'acquisition de nouveaux jobs
    W->>DB: Persister état et libérer les baux sûrs
    G-->>O: Arrêt terminé
```

**Recette :** une instance sans base ou sans confiance valide n'annonce pas une disponibilité IA ; son arrêt conserve les jobs récupérables.

### DEVAI-130 — Sauvegarde et reprise d'exploitation

**Origine :** Conception. **Responsable :** exploitation.

Les données centrales, versions anti-rejeu, paramètres administrés et clés selon leur coffre DOIVENT avoir une procédure de sauvegarde/restauration. Cibles proposées à confirmer : RPO de 24 heures et RTO de 4 heures. Après restauration, la gateway réconcilie les bornes de politiques, droits et réservations avant toute reprise IA afin de ne pas restaurer des autorisations révoquées ou du quota fictif.

**Recette :** un exercice restaure une base de qualification, vérifie ses empreintes et démontre la reprise sans réactiver une ancienne politique.

### DEVAI-131 — Compatibilité extension / moteur / gateway

**Origine :** Conception. **Responsable :** architecture / release.

Le handshake et les réponses de capacités DOIVENT exposer versions de protocole et fonctionnalités. Une version majeure incompatible bloque l'opération avec instruction de mise à jour. Les ajouts compatibles sont optionnels et ignorables selon le contrat. Les mises à jour de sécurité ne doivent pas faire croire qu'un ancien client respecte un mécanisme absent ; le serveur peut imposer une version minimale.

**Recette :** un moteur plus ancien non compatible ne cause ni crash de la vue ni contournement de contrôle.

### DEVAI-132 — Diagnostic exportable sans données sensibles

**Origine :** règles utilisateur ; Conception. **Responsable :** support / extension.

ODS DOIT proposer un diagnostic exportable sur action explicite : versions, capacités, IDs de corrélation, erreurs filtrées et statuts de dépendances. Les sources, prompts, credentials, usernames, hostnames, chemins absolus et remotes privés sont omis ou remplacés par placeholders. L'utilisateur prévisualise le contenu avant export ; aucune transmission de support n'est automatique.

**Recette :** un paquet de diagnostic synthétique ne révèle ni chemin utilisateur ni token et permet de retrouver l'erreur serveur via corrélation.

<a id="section-11"></a>

## 11. Développement, tests et livraison — DEVAI-133 à DEVAI-150

### DEVAI-133 — Build reproductible du dépôt ODS

**Origine historique (PDF 1.0) :** PDF p. 10–11 ; Utilisateur. **Responsable :** build / tech lead.

Le dépôt DOIT fournir un réacteur Maven parent pour moteur, bibliothèques partagées et gateway, avec versions de plugins fixées, tests unitaires et d'intégration inclus dans `verify`. L'extension utilise npm avec lockfile et scripts documentés pour compilation, lint, tests et VSIX. Le Java installé du poste ne doit pas modifier la cible de compilation. Les dépendances ne sont mises à jour que si nécessaire et compatible.

**Recette :** checkout propre, wrapper Maven en mode batch et `npm ci` produisent les artefacts attendus sans dépendances non déclarées.

### DEVAI-134 — Analyse initiale et protection Git

**Origine :** règles utilisateur. **Responsable :** équipe de développement.

Avant modification de code existant, l'équipe DOIT inventorier langages, versions, builds, tests, packaging, documentation, état Git et remotes. Elle préserve les changements locaux, vérifie les secrets, crée et pousse le backup requis avant le premier changement source, puis travaille sur `develop` ou une branche dédiée. Pour un nouveau projet applicatif, Git, `.gitignore`, premier commit, dépôt GitHub privé configuré et branche `develop` suivent les règles du développeur ; aucune identité ou URL n'est inventée. Un dossier servant uniquement à recevoir les présents documents n'est pas une application à initialiser automatiquement.

**Recette :** le rapport de développement identifie état initial, backup et branche ; une publication impossible est déclarée et ne devient pas un succès fictif.

### DEVAI-135 — Tests unitaires et intégration

**Origine historique (PDF 1.0) :** PDF p. 12 ; Utilisateur. **Responsable :** QA / développeurs.

Les tests DOIVENT couvrir les modules métier, les contrats IPC/REST, la base réelle en environnement isolé, les connecteurs via doubles et les intégrations de qualification. Les cas limites de politique, concurrence de quota, idempotence et chemins doivent être explicites. La couverture ne remplace pas des assertions métier ; aucun test ne peut être désactivé pour cacher un échec.

**Recette :** `verify` inclut les tests d'intégration configurés et un défaut d'autorisation provoqué est détecté par au moins un test négatif.

**Précision utilisateur 1.2 :** valider les parcours réseau des onglets avec GitLab, Mistral/LLM, Sonar et Xray simulés. Couvrir un parcours positif, les erreurs et refus ; conserver les adaptateurs réels, la vérification TLS/JWT et les contrôles de politique. Signaler séparément la qualification des instances réelles.

### DEVAI-136 — Tests de sécurité obligatoires

**Origine historique (PDF 1.0) :** PDF p. 12 ; Utilisateur. **Responsable :** QA sécurité.

La suite DOIT inclure projet inconnu/interdit/mauvaise instance, politique absente/expirée/modifiée/mal signée/rejouée, clés révoquées, changements de remote/branche/racine, accès gateway direct, transfert interprojets, secret synthétique, injection de prompt, permissions d'agents, quota concurrent, révocation, perte de connectivité et modèle non autorisé. Les tests supplémentaires couvrent traversée d'archive, IDOR, SSRF, XSS, reprise de jobs et absence de secrets dans les artefacts.

**Recette :** ces scénarios produisent des preuves de refus ou d'isolation, avec zéro appel IA quand l'autorisation échoue ; un fournisseur non Mistral est explicitement testé comme refusé en configuration initiale.

### DEVAI-137 — Commentaires explicatifs de toutes les classes et méthodes

**Origine :** Utilisateur. **Responsable :** tous développeurs / générateurs.

Chaque classe, interface, record, enum, constructeur, méthode et fonction nommée créée ou modifiée DOIT recevoir une documentation explicative proche de sa déclaration, y compris membres privés, tests et code de templates dont ODS produit le source. La documentation explique rôle, entrées/sorties, effets de bord, erreurs et contraintes pertinentes, et cite au moins une exigence réellement mise en œuvre au format `Exigences : DEVAI-xxx`. Un commentaire tautologique ou une référence sans rapport ne satisfait pas cette règle.

Java utilise Javadoc ; TypeScript/JavaScript JSDoc ou TSDoc ; Python des docstrings ; C# les commentaires XML. Les lambdas significatives sont expliquées au niveau de l'expression ou du bloc propriétaire. Les méthodes synthétiques créées par le compilateur et les sources de dépendances tierces non modifiées ne sont pas réécrites ; un source généré éditable livré dans un starter reste soumis au contrôle.

**Recette :** un contrôle par analyse syntaxique détecte classe, constructeur ou méthode sans commentaire ou référence ; une revue vérifie la pertinence sémantique des explications.

### DEVAI-138 — Traçabilité des exigences vers code et tests

**Origine :** Utilisateur ; Conception. **Responsable :** tech lead / CI.

Le dépôt DOIT maintenir `docs/traceability.csv` ou équivalent avec exigence, statut, modules, symboles, tests et preuves. Chaque ID référencé doit exister dans la spécification ; chaque exigence obligatoire doit avoir une couverture identifiée. Les tests portent leurs références. Pour du code applicatif produit dans un projet cible, une référence métier du projet est ajoutée lorsqu'elle existe ; ODS n'invente pas un `DEVAI-xxx` pour un besoin métier extérieur. Les règles ODS de génération peuvent être citées en complément.

**Recette :** la CI échoue sur ID inconnu ou documentation manquante dans le périmètre de code ; la matrice distingue `IMPLÉMENTÉ`, `TESTÉ`, `NON VÉRIFIABLE` et `À FAIRE`.

### DEVAI-139 — Documentation et changelog

**Origine :** règles utilisateur. **Responsable :** développement / documentation.

Chaque modification DOIT mettre à jour les documents impactés : installation, usage des cinq onglets, configuration, sécurité, architecture, API, exploitation, exemples et dépannage. Le projet versionné contient un CHANGELOG daté avec les changements réellement réalisés et les incompatibilités. Les captures et logs publiés utilisent données fictives et placeholders, sans identité privée ni chemin de poste.

**Recette :** les commandes du README sont vérifiées dans une installation propre et le changelog correspond au diff de la version.

### DEVAI-140 — Pipeline CI complet

**Origine historique (PDF 1.0) :** PDF p. 11–12 ; règles utilisateur. **Responsable :** CI.

Le pipeline DOIT enchaîner installation verrouillée, compilation, tests, contrôles de traçabilité, secrets, qualité/sécurité disponibles, packaging et validation des artefacts. Il préserve les workflows existants et utilise GitHub Actions pour le dépôt GitHub configuré, ou GitLab CI dans le contexte GitLab réel. Les credentials sont injectés par le mécanisme de secrets du fournisseur ; aucun job d'un contributeur non fiable ne reçoit les secrets de release.

**Recette :** un échec de test, de commentaire obligatoire ou de scan de secrets bloque la publication ; le pipeline expose le commit exact validé.

### DEVAI-141 — Packaging VSIX et Java

**Origine historique (PDF 1.0) :** PDF p. 11 ; Utilisateur. **Responsable :** release.

Le build DOIT produire un VSIX installable contenant l'extension, le moteur local et les ressources nécessaires, ainsi qu'un JAR exécutable de gateway distribué séparément. Les noms/version des fichiers proviennent du build. La liste des dépendances, licences et un inventaire logiciel sont fournis ; les artefacts ne contiennent ni comptes techniques, clés privées, sources inutiles ni cache local.

**Recette :** installation du VSIX sur un profil VS Code propre, handshake Java et démarrage de gateway avec configuration de qualification réussissent ; existence, taille et SHA-256 sont enregistrés.

### DEVAI-142 — Package autonome suiteInstall

**Origine :** règles utilisateur. **Responsable :** packaging.

La livraison DOIT préparer `suiteInstall/` hors Git avec `bin/`, `config/`, `docs/`, `scripts/`, `examples/` lorsque utiles, `README.md` et `VERSION`. Elle contient VSIX et gateway du dernier build, configurations d'exemple, scripts relatifs, contrats et documentation. Les prérequis externes Java 21, Python 3.12, Git, scanners et services sont indiqués : autonome signifie indépendant de l'arborescence source, pas un service complet sans infrastructure. Aucun répertoire vide artificiel n'est requis.

**Recette :** l'installation et un smoke test sont exécutés depuis une copie de ce dossier sans accès aux répertoires de build source.

### DEVAI-143 — Version unique et correspondance des artefacts

**Origine :** règles utilisateur. **Responsable :** release / build.

La version applicative DOIT provenir d'une source de vérité du dépôt et alimenter Maven, manifeste VSIX, `suiteInstall/VERSION`, tag `vX.Y.Z`, release et archive `suiteInstall-X.Y.Z.zip`. La version 1.0 de la présente spécification n'impose pas une version applicative déjà existante. Le build de release part du commit tagué validé ; métadonnées et empreintes identifient le commit réel, sans modification manuelle des binaires après validation.

**Recette :** les versions extraites des artefacts et le commit de build concordent avec le tag et les notes de release.

### DEVAI-144 — Exclusion Git des livrables générés

**Origine :** règles utilisateur. **Responsable :** Git / packaging.

`suiteInstall/`, `suiteInstall-*.zip`, sorties de build, VSIX/JAR de livraison et secrets DOIVENT être ignorés et vérifiés non suivis/non staged avant push. Les artefacts sont publiés dans une release ou mécanisme d'artefacts configuré, pas committés comme source. Un artefact précédemment suivi est retiré de l'index sans supprimer la copie locale utile ; l'historique distant n'est pas réécrit sans demande explicite.

**Recette :** la liste des fichiers suivis et indexés ne contient aucun package généré interdit.

**Exception historique utilisateur 1.2 (hors livraison 0.3.0) :** conserver également `suiteInstall` et sa configuration sur la branche `dev/toreprise`. Le package doit provenir de la release validée ; le commit d’instantané référence le commit source tagué et ne se substitue pas à lui. L’exception ne modifie pas les branches source. Seules les configurations fournies ou les valeurs de test/exemple existent à ce stade ; aucun secret réel d’organisation n’est inventé ni prélevé dans le poste.

### DEVAI-145 — Recette du package autonome

**Origine :** évolution utilisateur du 29 octobre 2026.

Vérifier versions, tailles, empreintes, configuration compilée, gateway et Vibe embarqués, licences et compatibilité VS Code. Tester le démarrage et l’arrêt possédés. Les artefacts de démonstration ne contiennent pas de secret réel ; les distributions privées peuvent contenir les secrets explicitement autorisés selon DEVAI-035 et DEVAI-176. Un JAR présent ne vaut pas test d’exécution.

**Recette :** tests déterministes des parcours positifs, refus et erreurs ; preuves réelles consignées dans `docs/VALIDATION.md`.

### DEVAI-146 — Merge, tag et publication

**Origine :** règles utilisateur. **Responsable :** mainteneur / release.

Après validation complète, diff et secrets vérifiés, les changements DOIVENT être committés et poussés sur la branche de développement ; les contrôles CI applicables précèdent le merge vers `main`. Le tag vise exactement le commit stable validé. La release joint archive, documentation et empreintes via le fournisseur réellement disponible : GitHub Release prioritaire pour le dépôt GitHub, autre mécanisme explicitement configuré sinon. Aucune URL Artifactory n'est inventée. L'existence distante de l'asset est vérifiée avant succès.

```mermaid
sequenceDiagram
    participant D as Branche de développement
    participant CI as Pipeline
    participant M as Main et tag
    participant R as Release configurée
    D->>CI: Commit et contrôles
    CI-->>D: Preuves de validation
    D->>M: Merge autorisé du code validé
    M->>CI: Build du commit stable exact
    CI-->>M: Artefacts et validations de ce commit
    M->>M: Tag de version sur le commit validé
    M->>R: Publier notes, archive et empreintes
    R-->>M: Confirmer présence et métadonnées distantes
```

**Recette :** le commit du tag, celui des preuves et celui des artefacts sont identiques ; une CI rouge empêche release et nettoyage des livrables non publiés.

### DEVAI-147 — Mode de démonstration clairement isolé

**Origine :** Conception. **Responsable :** développeurs / QA.

Le dépôt DOIT offrir des fixtures synthétiques et doubles de services pour développer et tester sans credentials de production. Ce mode est explicitement identifié dans l'interface et les rapports, désactivé par défaut en production et sans données privées. Il couvre succès et erreurs ; il ne remplace pas les adaptateurs réels ni les tests d'intégration de qualification.

**Recette :** un démarrage production ne peut sélectionner un adaptateur factice et une démonstration ne prétend pas avoir scanné un vrai projet.

### DEVAI-148 — Lots de réalisation sans réduction de périmètre

**Origine historique (PDF 1.0) :** PDF p. 11–12 ; Utilisateur. **Responsable :** pilotage / tech lead.

La réalisation DOIT suivre des incréments vérifiables : socle/gouvernance ; quatre starters ; IA Mistral, agents et quotas ; Quality/Security ; GitFlow et industrialisation. Les contrats et tests transverses commencent dès le socle. Les cinq vues peuvent être présentes tôt avec états « indisponible », mais la livraison complète ne s'arrête pas à cette maquette ou au seul starter Java.

**Recette :** chaque lot a une démonstration et ses preuves ; les exigences restantes restent visibles jusqu'à réalisation ou blocage explicitement déclaré.

### DEVAI-149 — Rapport final véridique

**Origine :** règles utilisateur. **Responsable :** équipe de réalisation.

Le rapport DOIT indiquer modifications, fichiers principaux, tests/commandes/nombres réellement observés, build/artefacts, livraison, documentation, branche, backup, commit, push, tag, release, risques et contrôles non réalisables. Il comprend version, package, destination et statuts `SUCCESS`, `FAILED`, `NON EXÉCUTÉ`, `NON RÉALISÉE` ou `NON VÉRIFIABLE` selon l'opération. Aucune compilation ou publication n'est déclarée réussie sans exécution vérifiée.

**Recette :** un lecteur peut distinguer code implémenté, tests sur doubles, intégration réelle, package local et publication distante.

### DEVAI-150 — Acceptation finale de la solution

**Origine historique (PDF 1.0) :** PDF p. 12 ; Utilisateur ; Conception. **Responsable :** architecture / QA / sécurité.

L'acceptation DOIT vérifier la couverture des 166 exigences, les quatre starters remplaçables, les cinq vues fonctionnelles, l'agent de développement outillé de bout en bout, Java/Maven, l'autorisation initiale Mistral seule, les commentaires traçables, l'autorité serveur, les tests négatifs, les connecteurs réels qualifiés et la distribution validée. Les risques résiduels et décisions d'environnement sont approuvés par leurs responsables. Une exception est documentée avec impact et ne transforme pas un contrôle manquant en succès.

**Recette :** la matrice de conformité et les preuves de livraison permettent de décider de la mise en production sans dépendre d'une déclaration narrative de l'IA.

<a id="section-12"></a>

## 12. Agent de développement intégré — DEVAI-151 à DEVAI-160

### DEVAI-151 — Expérience IDE avec modes Question, Modification et Agent

**Origine :** Utilisateur ; Conception. **Responsable :** IA / extension / agents.

Dans IA Code Generator, le chat et les prompts approuvés DOIVENT utiliser le même moteur de tâches. Le mode **Question** explore et explique sans mutation ; le mode **Modification** propose des changements ciblés avec diff ; le mode **Agent** accomplit une tâche de développement complète en planifiant, appelant les outils, modifiant, exécutant, observant et corrigeant jusqu'à une condition d'arrêt explicite. L'expérience s'inspire des parcours de Codex, Continue et Copilot sans dépendre de leurs services, marques, implémentations ni modèles. Mistral reste le seul fournisseur initial autorisé.

La vue DOIT afficher conversation, mode, projet, contexte retenu, plan, étapes en cours, appels d'outils/résultats résumés, modifications, validations, tokens et permissions. Elle propose lancer, arrêter, reprendre, préciser la demande, ouvrir le diff et inspecter une commande. Les appels d'outils sont des événements visibles, pas une promesse narrative d'action. Aucune chaîne de pensée privée du modèle n'est nécessaire : seuls plan, décisions utiles, actions et résultats sont présentés.

**Recette :** une demande « ajoute un endpoint et ses tests » peut conduire l'agent autorisé à lire, modifier plusieurs fichiers, lancer Maven, corriger un échec et présenter un résultat vérifié dans la même conversation.

### DEVAI-152 — Boîte à outils de développement complète et extensible

**Origine :** Utilisateur ; Conception. **Responsable :** agent / moteur / connecteurs.

L'agent DOIT disposer d'un registre d'outils typés couvrant exploration, recherche, lecture, écriture contrôlée, exécution, tests, Git, documentation et analyses. Les outils sont identifiés/versionnés avec JSON Schema d'entrée/sortie, exigences, permissions, timeout, portée filesystem/réseau et mode d'approbation. « Tous les outils nécessaires » signifie ces capacités complètes pour les quatre stacks prises en charge, sous permissions administrées ; cela n'accorde pas un accès illimité au poste ou aux services.

| Famille | Outils ODS à implémenter ou adapter | Effet / contrôle |
|---|---|---|
| Projet | `workspace.list`, `project.detect`, `project.instructions` | Arborescence bornée, stack, builds, règles du dépôt |
| Recherche | `search.text`, `search.files`, `code.symbols`, `code.references` | Recherche texte/regex, symboles et références via capacités IDE disponibles |
| Lecture | `files.read`, `files.readRanges`, `editor.selection`, `diagnostics.read` | Fichiers autorisés, sélection et diagnostics ; exclusions appliquées |
| Édition | `changes.propose`, `changes.apply`, `files.create`, `files.move`, `files.delete` | Plan de changement et empreintes ; mêmes contrôles que les diff, y compris création/suppression |
| Exécution | `process.start`, `process.output`, `process.cancel`, `terminal.open` | Arguments typés, commande visible, cwd et environnement contrôlés |
| Validation | `build.run`, `tests.run`, `lint.run`, `app.start`, `app.stop` | Profils du projet et capture des preuves |
| Dépendances | `dependencies.inspect`, `dependencies.resolve`, `dependencies.change` | Registres approuvés, plan explicite, locks réels |
| Git | `git.status`, `git.diff`, `git.log`, `git.branch`, `git.commit`, `git.push`, `gitlab.mr.prepare`, `gitlab.mr.create` | Lecture libre dans le périmètre ; mutations et publication gouvernées |
| Qualité / sécurité | `quality.scan`, `quality.report`, `security.scan`, `security.report` | Jobs Sonar/Xray réels et droits correspondants |
| Documentation | `docs.search`, `docs.fetch`, `docs.update` | Sources officielles ou organisationnelles autorisées ; contenu externe non fiable |
| Tâches | `task.plan`, `task.checkpoint`, `task.status`, `task.report` | Plan, avancement, reprise et preuves |
| Tests d'application | `http.check`, `ui.tests.run` | App de test démarrée explicitement, hôtes approuvés, harness du projet |

Ces noms décrivent les contrats ODS à développer et ne sont pas supposés exister dans VS Code. Les fournisseurs de symboles peuvent être absents : l'agent revient alors à la recherche textuelle en l'indiquant. Un protocole de connecteur externe tel que MCP peut être ajouté ultérieurement, mais aucun serveur d'outils choisi arbitrairement par un développeur ne contourne le registre et la politique.

**Recette :** le catalogue expose ces familles ; l'agent exécute réellement les outils essentiels et un outil inconnu, non autorisé ou mal paramétré est rejeté avant effet.

### DEVAI-153 — Boucle autonome planifier, agir, observer, corriger

**Origine :** Utilisateur ; Conception. **Responsable :** orchestrateur Java / gateway.

Une tâche en mode Agent DOIT se poursuivre tant que son objectif n'est pas vérifié, qu'aucun blocage utile n'exige une réponse et que budget/durée/permissions restent valides. L'orchestrateur construit le contexte, appelle le modèle Mistral approuvé capable de tool calling, valide les appels structurés, exécute les outils autorisés et réinjecte leurs résultats filtrés. Il peut adapter le plan après une erreur de build et réessayer les corrections justifiées, sans ignorer l'échec. Un texte ressemblant à une commande ne vaut pas un appel d'outil valide.

```mermaid
sequenceDiagram
    actor D as Développeur
    participant E as IA Code Generator
    participant J as Orchestrateur Java local
    participant G as Gateway et politique
    participant M as Mistral approuvé
    participant T as Outils workspace
    D->>E: Objectif et mode Agent
    E->>J: Tâche, contexte et permissions de session
    loop Objectif non vérifié et budget disponible
      J->>G: Étape IA et contexte filtré
      G->>G: Autoriser AGENT_RUN, modèle et quota
      G->>M: Conversation et schémas des outils autorisés
      M-->>G: Appels structurés ou résultat final
      G-->>J: Proposition d'action normalisée
      J->>J: Valider schéma, portée et consentement
      opt Nouvelle autorisation réellement nécessaire
        J-->>E: Action précise à approuver
        D->>E: Autoriser ou refuser
        E->>J: Décision bornée
      end
      J->>T: Exécuter outil autorisé
      T-->>J: Résultat et code de retour
      J-->>E: Progression, diff et preuves
    end
    J-->>E: Bilan vérifié ou blocage explicite
```

Les outils distants passent par la gateway et ses droits ; les outils locaux sont exécutés par le moteur, les accès natifs éditeur par l'extension. Aucun fournisseur IA ne reçoit directement un canal shell ou un secret de poste.

**Recette :** un premier test en échec provoque analyse et correction dans la même tâche, puis un nouveau test ; le résultat final correspond aux sorties observées.

### DEVAI-154 — Édition multifichiers et contexte de travail vivant

**Origine :** Utilisateur ; Conception. **Responsable :** agent / moteur / extension.

L'agent DOIT pouvoir créer et modifier des fichiers de plusieurs modules du même projet, ajouter tests et documentation, déplacer/supprimer des fichiers lorsqu'autorisé, et suivre les buffers ouverts. Toutes les mutations, même demandées via `files.create` ou `files.delete`, passent par le gestionnaire de changements, les empreintes de base et les exclusions. Les résultats de recherche et diagnostics sont rafraîchis après modification ; aucune indexation globale ne doit contourner l'isolation des racines.

**Recette :** une tâche modifie service, test et README de manière cohérente ; une modification humaine concurrente déclenche une réconciliation sans écrasement.

### DEVAI-155 — Terminal et commandes de développement

**Origine :** Utilisateur ; Conception. **Responsable :** exécuteur d'outils.

L'agent DOIT pouvoir lancer les commandes nécessaires au projet : build Maven, scripts npm existants, outils Python/.NET, tests, lint, résolution de dépendances et processus de développement approuvés. Les processus longs possèdent un identifiant, un flux de sortie borné, un code de retour final et une commande d'arrêt. Le terminal visible peut être ouvert pour inspection ; il n'est pas la seule preuve du résultat.

Une commande nouvelle est autorisée par le registre, sa catégorie et une permission de session applicable, ou fait l'objet d'une approbation ciblée. Si une tâche nécessite un script shell, ce script doit être explicite, analysé, identifié par empreinte et autorisé comme tel ; il n'est pas exécuté via une chaîne arbitraire non examinée. Les droits de DEVAI-073 restent applicables. Les installations et accès réseau ne sont pas cachés dans un build présenté comme sans effet de bord.

**Recette :** l'agent lance `mvnw` adapté à l'OS, suit une sortie Maven, détecte le code retour, arrête un serveur de test qu'il a créé et ne tue pas un processus utilisateur étranger.

### DEVAI-156 — Diagnostic et vérification fonctionnelle outillés

**Origine :** Utilisateur ; Conception. **Responsable :** agent / QA.

L'agent DOIT utiliser erreurs de compilation, diagnostics éditeur, sorties de tests, stack traces filtrées et résultats HTTP/UI disponibles pour localiser et corriger un défaut. Il détecte les tests et harness existants avant d'en créer un. Les smoke tests d'application sont limités aux endpoints de test autorisés ; les tests UI utilisent le framework du projet ou un adaptateur qualifié, sans présumer une automatisation navigateur déjà installée. Une capacité manquante est signalée et ne produit pas une preuve inventée.

**Recette :** sur les projets de référence, l'agent démontre au minimum compilation/tests sur les quatre stacks et démarrage/contrôle HTTP d'un profil web qualifié.

### DEVAI-157 — Plan, mémoire de tâche et reprise

**Origine :** Utilisateur ; Conception. **Responsable :** agent / sessions.

L'agent DOIT maintenir un objectif, un plan d'étapes et un journal compact des décisions, outils, fichiers touchés et validations. Un point de reprise contient les empreintes et références nécessaires, sans secrets ni stockage central de sources interdit. Les conversations longues peuvent être résumées pour respecter le budget, en conservant exigences, contraintes et résultats ; le résumé n'est pas une preuve suffisante de l'état actuel du disque.

Après redémarrage ou reprise, l'agent relit l'état réel et réévalue droits/politique avant action. Les limites de 10 étapes/10 minutes constituent des valeurs initiales administrables, pas une impossibilité de mener un développement long : l'utilisateur peut poursuivre avec un budget autorisé ou plusieurs checkpoints sans changer d'objectif. Atteindre une limite suspend la tâche proprement et l'annonce.

**Recette :** une tâche reprise retrouve ses changements, détecte une modification externe et continue sans refaire un push ni perdre les contraintes utilisateur.

### DEVAI-158 — Permissions de session et autonomie graduée

**Origine :** Utilisateur ; PDF p. 9 ; Conception. **Responsable :** sécurité / UX agent.

Les lectures, recherches et diagnostics autorisés DOIVENT s'exécuter sans demandes répétitives. Une permission de session peut autoriser l'édition d'un périmètre et l'exécution de catégories de commandes connues ; elle est visible, révocable, limitée au projet, à la tâche et à une échéance. L'agent continue dans ce périmètre sans demander à chaque fichier. Les nouvelles destinations réseau, suppressions importantes, installations non couvertes, publication/push/MR et privilèges supplémentaires exigent une autorisation distincte applicable. Le consentement utilisateur ne dépasse jamais une interdiction organisationnelle.

**Recette :** une correction autorisée de cinq fichiers ne déclenche pas cinq confirmations identiques ; une tentative d'envoi vers un hôte nouveau est arrêtée et expliquée.

### DEVAI-159 — Prompts approuvés comme tâches exécutables

**Origine :** Utilisateur ; PDF p. 9 ; Conception. **Responsable :** prompts / agents.

L'exécution d'un prompt dans l'onglet IA DOIT pouvoir démarrer une tâche outillée, pas seulement coller du texte dans le chat. Le manifeste précise mode Question/Modification/Agent, variables, objectif, critères de succès, outils requis, permissions maximales et budget. Les prompts de revue peuvent rester en lecture seule ; les prompts de génération/remédiation peuvent exploiter le cycle complet si les droits correspondants et `AGENT_RUN` sont accordés. Une action Sonar ou Xray ne gagne pas plus de droits en étant exécutée sous forme de prompt générique.

**Recette :** un prompt « implémenter une exigence » reçoit un ID existant, explore, modifie, documente, teste et fournit les preuves ; un prompt de revue ne modifie rien.

### DEVAI-160 — Recette de l'agent complet

**Origine :** Utilisateur ; Conception. **Responsable :** QA / tech lead.

La recette DOIT démontrer : explication d'un module sans écriture ; création d'une fonctionnalité multifichiers et de ses tests ; correction d'un défaut à partir d'un test en échec ; exécution d'un prompt approuvé ; arrêt/reprise ; modification humaine concurrente ; refus d'un outil hors périmètre ; limite de quota ; refus d'un modèle autre que Mistral ; absence de secret dans les messages et sorties. Un chat qui renvoie uniquement des blocs de code sans agir sur le workspace ne satisfait pas ces exigences.

**Recette :** une démonstration de bout en bout produit appels d'outils réels, diff, codes retour et résultats de tests, et permet de relier chaque action à l'exigence, au projet et à la permission de session.

<a id="section-12bis"></a>

## 12 bis. Prompts système et recommandations organisationnelles — DEVAI-161 à DEVAI-166

### DEVAI-161 — Prompt système global administré

**Origine :** Utilisateur ; Conception. **Responsable :** gouvernance / gateway / prompts.

La solution DOIT prendre en charge un fichier de prompt système global contenant les recommandations et exigences de développement communes de l'organisation. Il est chargé côté gateway depuis un bundle administré, signé et versionné, puis appliqué automatiquement à tous les parcours IA : chat, génération, revue, agents, remédiations et description de MR. Il ne dépend pas de la sélection manuelle d'un prompt de tâche. Le développeur peut connaître sa version et consulter son contenu si ses droits le permettent, mais ne peut le supprimer, le remplacer ou le contourner par un message utilisateur.

Le fichier source peut être Markdown ou texte UTF-8 ; son chemin relatif, sa version et son SHA-256 sont inscrits dans un manifeste signé. Un prompt n'accorde jamais une permission d'outil, un fournisseur ou un transfert interdit par les contrôles serveur. Les règles vérifiables mécaniquement restent également appliquées par validateurs, tests et CI : une instruction textuelle au modèle ne garantit pas à elle seule la conformité.

**Recette :** une demande libre sans prompt de tâche sélectionné reçoit quand même le socle global ; une tentative de l'annuler dans le chat ne modifie ni sa version ni les droits effectifs.

### DEVAI-162 — Fichiers de recommandations par ligne de développement

**Origine :** Utilisateur ; Conception. **Responsable :** standards / contexte / prompts.

Le bundle DOIT fournir des profils distincts pour Java, Angular, Python et .NET. Chaque profil peut référencer plusieurs fichiers de recommandations : architecture, conventions, tests, sécurité, dépendances et livraison. Le moteur détecte la stack des modules/fichiers ciblés et la gateway résout les profils administrés correspondants. Le profil Java impose Maven. La sélection des règles est explicite dans la provenance de contexte ; une stack inconnue utilise uniquement le socle global si la politique le permet, sinon l'action est bloquée avec diagnostic.

Dans un monorepo, une tâche multifichiers applique le socle global une fois puis les profils pertinents, clairement rattachés à leurs modules et fichiers. Les règles Angular ne sont pas appliquées à une classe Java. Les sous-tâches peuvent être séparées pour préserver ce rattachement et le budget de contexte. L'utilisateur ne peut déclarer une autre stack pour échapper au profil détecté ; toute ambiguïté est résolue avant les mutations concernées.

**Recette :** une tâche Java/Angular utilise les deux profils aux bons emplacements ; un projet Python utilise le profil Python et le socle commun, sans recommandations Java parasites.

### DEVAI-163 — Contenus génériques livrés immédiatement

**Origine :** Utilisateur. **Responsable :** équipe de réalisation / packaging.

La première version DOIT livrer de vrais fichiers génériques utiles, et non des fichiers vides ou de simples placeholders : un socle global et quatre profils de stack. Ces fichiers sont identifiés comme `GENERIC`, versionnés, signables et testés. Ils ne doivent pas prétendre représenter des standards internes qui n'ont pas encore été fournis. Leur application ne dépend pas de l'existence d'un dépôt de recommandations organisationnelles ; le catalogue générique approuvé peut être distribué avec la gateway.

| Fichier générique à créer | Contenu minimal initial |
|---|---|
| `system/global.md` | Analyser l'existant ; changements minimaux ; préserver le travail ; ne pas inventer API ni succès ; filtrer secrets ; respecter permissions ; commenter classes/méthodes et référencer exigences ; mettre à jour tests/docs ; vérifier les résultats |
| `stacks/java.md` | Java 21 et Maven/wrapper pour les nouveaux composants ; séparation des responsabilités ; validation des entrées ; gestion explicite des erreurs ; tests unitaires/intégration ; Javadoc et références ; compatibilité des dépendances |
| `stacks/angular.md` | CLI officielle et versions compatibles ; typage strict ; composants/services cohérents ; accessibilité ; gestion d'erreur ; tests ; éviter DOM non fiable ; JSDoc/TSDoc et références |
| `stacks/python.md` | `pyproject.toml`, environnement virtuel et layout `src` ; dépendances déclarées ; annotations de type adaptées ; exceptions explicites ; tests ; docstrings et références ; aucun secret dans le package |
| `stacks/dotnet.md` | SDK fixé ; conventions C# ; séparation des responsabilités ; validation des entrées ; gestion d'erreur ; tests ; dépendances NuGet approuvées ; documentation XML et références |

**Recette :** une installation neuve peut afficher et appliquer ces cinq fichiers sans contenu organisationnel privé ; des tests vérifient qu'ils ne sont ni vides ni des copies indifférenciées du même texte.

### DEVAI-164 — Livraison et remplacement par les vrais fichiers de l'organisation

**Origine :** Utilisateur ; Conception. **Responsable :** administrateurs / CI gouvernance.

L'organisation DOIT pouvoir ajouter des prompts de tâches et publier ses propres fichiers de recommandations dans le dépôt GitLab gouverné, au moyen d'un manifeste qui indique ID, version, portée globale/stack, fichiers, empreintes, date de validité et mode de substitution. Deux modes sont prévus : `REPLACE` remplace le contenu générique d'une portée ; `APPEND` lui ajoute les fichiers organisationnels dans un ordre explicite. Le remplacement peut concerner uniquement Java, uniquement le global ou plusieurs profils ; les autres profils génériques restent actifs si la politique l'autorise.

Les administrateurs prévisualisent le prompt effectif et valident schéma, signatures, cohérence et tests avant publication atomique à un commit immuable. Aucun changement de code ni nouvelle publication du VSIX n'est nécessaire pour ajouter un prompt ou substituer un fichier dans les formats déjà pris en charge. Le contenu actif est marqué `ORGANIZATION`. Un profil organisationnel obligatoire absent, expiré ou invalide bloque les actions IA concernées ; il n'est jamais remplacé silencieusement par le générique. Le repli vers le générique doit être explicitement permis. Le rollback se publie sous une version de bundle supérieure, conformément à l'anti-rejeu.

```mermaid
sequenceDiagram
    actor A as Administrateur standards
    participant CI as CI de gouvernance
    participant L as GitLab
    participant G as Gateway
    participant M as Mistral autorisé
    A->>CI: Fichiers globaux et par stack, manifeste et mode
    CI->>CI: Valider, prévisualiser, tester et signer
    CI->>L: Publier le bundle au commit immuable
    G->>L: Charger le nouveau bundle et ses fichiers
    G->>G: Vérifier signature, empreintes, dates et version
    G->>G: Activer atomiquement le bundle valide
    G->>M: Prochaine requête avec prompt effectif recomposé
    Note over G,M: Aucun changement du VSIX ni nouvelle permission implicite
```

**Recette :** remplacer `stacks/java.md` par un vrai fichier organisationnel change le prompt Java dès le rafraîchissement autorisé, laisse Python inchangé et ne nécessite pas de rebuild de l'extension.

### DEVAI-165 — Composition déterministe et gestion des contradictions

**Origine :** Utilisateur ; Conception. **Responsable :** gateway / assemblage des prompts.

Pour chaque appel IA et chaque tour d'agent, la gateway DOIT composer : contraintes de sécurité ODS non surchargeables, socle global résolu, profils de stacks applicables, instructions du prompt de tâche approuvé, puis demande utilisateur et contexte comme données non fiables. Ce classement est une hiérarchie logique appliquée par le serveur ; il ne suppose pas que le fournisseur possède un rôle API distinct pour chaque couche. Un fichier trouvé dans le workspace ne devient jamais un prompt système organisationnel sans publication par la chaîne de confiance.

Chaque règle organisationnelle distingue exigence obligatoire et recommandation, idéalement avec un ID stable propre à l'organisation tel que `ORG-JAVA-001`. Le profil de stack peut préciser une règle globale compatible ; il ne peut contredire une obligation globale. Une contradiction entre exigences obligatoires empêche l'activation du bundle ou l'action concernée avec un diagnostic ; une exception doit être explicitement approuvée et publiée dans la gouvernance, jamais déduite par le modèle. Les préférences locales et prompts de tâches ne peuvent affaiblir une obligation organisationnelle.

Le budget réserve d'abord les instructions obligatoires puis le contexte utile ; aucune règle obligatoire n'est tronquée silencieusement. Si le bundle requis dépasse la capacité du modèle Mistral autorisé, l'action est refusée ou découpée avec préservation des règles pertinentes. Un changement de bundle entre deux tours d'agent impose recomposition, réévaluation du plan et invalidation des approbations devenues incompatibles.

**Recette :** le prompt effectif est déterministe pour les mêmes versions et paramètres ; conflit, dépassement de budget et injection locale ne conduisent pas à ignorer une exigence obligatoire.

### DEVAI-166 — Provenance, inspection et recette des recommandations

**Origine :** Utilisateur ; Conception. **Responsable :** audit / extension / QA.

L'interface DOIT afficher l'origine `GENERIC` ou `ORGANIZATION`, le bundle/version, le profil global et les profils de stack actifs. Une vue de diagnostic permet de consulter les instructions effectives selon les droits, sans exposer de secret. Chaque appel audite versions, empreintes et profils utilisés ; les règles peuvent être relues via leur commit de gouvernance sans journaliser systématiquement leur texte complet. Le code généré référence les exigences DEVAI applicables et les IDs organisationnels réels lorsqu'il les implémente ; aucun ID organisationnel n'est inventé.

La recette DOIT couvrir : démarrage avec génériques, ajout d'un prompt de tâche, remplacement global, remplacement d'une seule stack, ajout `APPEND`, monorepo, bundle signé invalide, fichier manquant, conflit, rollback versionné, absence de repli implicite, dépassement de contexte et actualisation d'un agent en cours. L'implémentation maintient les commentaires, tests et documentation correspondant à ces comportements.

**Recette :** un auditeur retrouve les règles exactes ayant gouverné une génération ; les tests démontrent le passage des fichiers génériques aux fichiers organisationnels sans modification de l'extension et sans élargissement de droits.


## 13. VSIX administré et analyses locales — DEVAI-167 à DEVAI-181

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

## 14. Évolution réalisée 0.5.0

### DEVAI-182 - Correction automatique validée

Dans Quality et Security, Corriger les issues lance l'agent sur les anomalies du rapport initial sans consigne complémentaire ni sélection manuelle du patch. Le projet doit être propre dans Git et son empreinte identique au rapport. Une nouvelle branche `fix/ods-*` et des checkpoints conservent les changements. Le LLM applique les propositions ; le contrôleur exécute ensuite compilation, tests et nouveau scan. En cas d'échec, la tentative suivante reçoit le rapport initial et les diagnostics. Le diff n'est ouvert qu'après validation de la compilation et des tests. Une annulation conserve les modifications ; aucun commit ni push automatique n'est créé. Les conflits de branche, buffers ou sources interrompent le parcours.

### DEVAI-183 - Augmentation de couverture mesurée

Quality propose un objectif de couverture et le bouton Augmenter la couverture. Les mesures agrégées et par fichier proviennent du rapport Sonar réel ; une mesure absente n'est pas remplacée par zéro. L'agent modifie les tests et leur instrumentation, sans réécrire le code de production pour augmenter artificiellement le taux. Compilation, tests et nouvelle mesure sont exécutés après application. Si l'objectif n'est pas atteint, les mesures actualisées alimentent la tentative suivante. Les fichiers initialement couverts et leurs dénominateurs sont contrôlés ; les exclusions et désactivations de tests sont refusées.

### DEVAI-184 - Rapport de correction et PDF

Chaque anomalie, vulnérabilité ou lacune de couverture initiale conserve son identifiant, sa criticité et son constat. Le bilan distingue corrigé, corrigé partiellement et non implémenté. Les explications déclaratives du LLM restent distinctes des preuves de compilation, tests et scan. Une proposition non appliquée n'est pas présentée comme une correction partielle. Le rapport affiche les fichiers, la branche, les tentatives et les diagnostics ; des icônes et libellés rendent les états compréhensibles. Le téléchargement PDF utilise les données détenues par le contrôleur natif, avec masquage des secrets et chemins privés. Le rapport reste en mémoire jusqu'au rechargement de la fenêtre ; le PDF exporté constitue la copie conservable.

### DEVAI-185 - Plafond de tentatives utilisateur

Les onglets Quality et Security exposent un nombre maximal de tentatives entier de 1 à 100, initialement 3 et mémorisé dans le workspace. La correction et l'augmentation de couverture utilisent cette valeur, sans plafond fixe de cinq tentatives. Une tentative peut comporter plusieurs échanges d'outils Mistral ; ce nombre ne représente donc pas un nombre d'appels API. Arrêt utilisateur, quotas, conflits et erreurs bloquantes peuvent terminer le parcours avant le plafond. L'atteinte de la limite produit un bilan honnête des problèmes restants.

### DEVAI-186 - Pré-revue de Merge Request

GitFlow alimente la description de MR avec les commentaires du développeur et une pré-revue LLM par fichier modifié : résumé, recommandation au reviewer, clarification demandée au développeur si nécessaire et vérification suggérée. Le contexte provient des commits publiés et de leur comparaison GitLab. La réponse doit couvrir exactement les fichiers retenus ; le texte est présenté avant publication. Les SHA source et cible sont revérifiés, les brouillons obsolètes refusés et les MR existantes ne sont pas écrasées. Les tests suggérés ne sont jamais présentés comme exécutés.

### DEVAI-187 - Intégrité UTF-8 des propositions

Le protocole JSON-RPC du moteur utilise explicitement UTF-8 en entrée et en sortie, indépendamment de l'encodage de console Windows. Accents et caractères non latins doivent être conservés entre proposition et application pour que l'empreinte approuvée reste valide. Les erreurs d'outils sont visibles et intégrées aux diagnostics du rapport après filtrage.

Ces exigences précisent DEVAI-175 et remplacent, pour les boutons de correction automatique, les anciens passages imposant une sélection du mode ou du patch. Les parcours manuels de modification et de documents GitFlow conservent leurs confirmations propres. Le banc Test1 a été validé avec Mistral Medium 3.5, SonarQube local et mockXray ; les résultats historiques sont dans `TestDOKERMan/VALIDATION.md`. Aucune nouvelle exécution de tests n'est requise pour cette mise à jour documentaire exceptionnelle demandée par l'utilisateur.

## 15. Contrats et preuves de réalisation

Les contrats exécutables sont les sources du dépôt : `vscode-extension/src/protocol.ts`, les contrôleurs de `ods-gateway`, `scripts/local_analysis.py` et `scripts/vibe_bridge.py`. Les exemples administrateur se trouvent dans `config/organisations/demo`. Les procédures sont décrites dans `docs/CONFIGURATION.md`, `docs/INSTALLATION.md` et `docs/API.md`.

La recette couvre le refus Git, le cycle de vie gateway, les deux transports Mistral, l’envoi Entrée, les quatre générateurs SBOM, les deux authentifications Xray, la pagination et la provenance Sonar, l’annulation et les rapports obsolètes. Tous les services métier sont remplacés par des doubles. Les tests avec instances organisationnelles, leurs licences, permissions et modèles approuvés doivent être distingués explicitement de ces tests simulés.

Références de contrat : [Vibe](https://github.com/mistralai/mistral-vibe), [SonarScanner .NET](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/scanners/dotnet/using), [CycloneDX npm](https://github.com/CycloneDX/cyclonedx-node-npm), [CycloneDX Python](https://github.com/CycloneDX/cyclonedx-python), [CycloneDX .NET](https://github.com/CycloneDX/cyclonedx-dotnet). Le contrat Graph Scan est rapproché du code local de référence `scan-sbom-xray` sans modifier ce projet.
