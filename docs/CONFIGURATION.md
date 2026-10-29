# Configuration et gouvernance

Toutes les URL sont externes au code et au VSIX. Les exemples figurent dans `config/gateway.env.example`. La configuration machine `ods.gatewayUrl` pointe l'extension vers la gateway ; aucun secret Mistral, Sonar ou JFrog ne va dans la webview.

## Identité et projets

Configurer un client OIDC public avec PKCE, sans secret client. Autoriser le callback `vscode://ods-internal.organisation-development-standard/callback` (adapter le schéma pour une distribution VS Code différente). Les access tokens doivent contenir l'audience `ods-gateway`, un sujet et le claim vérifié `gitlab_user_id`. Configurer l'émission de refresh tokens selon la politique du SSO. Les jetons utilisateur sont stockés dans SecretStorage, séparément pour chaque origine de gateway.

Le GitLab configuré dans `ODS_GITLAB_URL` est la seule instance autorisée. Le remote local doit correspondre à cette instance. Le développeur fournit son autorisation GitLab ; la gateway interroge `/user` et l'accès au projet. Le compte de gouvernance ne remplace jamais le compte utilisateur pour une MR.

## Politique IA signée

Adapter `examples/ai-project-policy.json` : instance, IDs réels, actions, alias, quotas et dates. Les dates de cet exemple ne constituent pas une politique active ; renouveler les dates avec une validité maximale de 24 heures. Publier `ai-project-policy.json` et sa signature `ai-project-policy.sig` dans le projet GitLab de gouvernance, au même commit. L'extension ne décide jamais du droit IA.

La clé publique DER Ed25519 encodée Base64 est fournie par `ODS_POLICY_PUBLIC_KEY`. La clé privée reste hors Git et hors de l'application. Les versions sont monotones en base ; un rollback se publie sous un numéro supérieur. La politique est relue au maximum toutes les 60 secondes et son expiration est vérifiée à chaque usage.

```text
java -cp <ENGINE_JAR> org.organisation.ods.security.GovernanceMain keygen <PRIVATE_DIRECTORY>
java -cp <ENGINE_JAR> org.organisation.ods.security.GovernanceMain sign <POLICY_JSON> <PRIVATE_DIRECTORY>/signing.key
```

## Prompts organisationnels

Les fichiers génériques de `prompt-content/` fonctionnent immédiatement. Pour préparer un exemple organisationnel depuis le checkout :

```powershell
.\scripts\prepare-prompt-bundle.ps1 -Destination .ods-local\prompt-bundle -Version 1
```

Réviser les vrais fichiers avant signature. Signer `bundle.json`, puis exécuter :

```text
java -cp <ENGINE_JAR> org.organisation.ods.security.GovernanceMain verify-prompts <BUNDLE_DIRECTORY> <PUBLIC_KEY_FILE>
```

La CI de gouvernance publie et déploie atomiquement le répertoire vérifié depuis un commit immuable. Configurer `ODS_PROMPT_DIRECTORY` et `ODS_PROMPT_PUBLIC_KEY` côté gateway. Le contenu est rechargé à chaque tour, sans rebuild du VSIX. Les tâches supplémentaires apparaissent après Actualiser. `REPLACE` remplace une portée et `APPEND` ajoute les fichiers dans l'ordre déclaré. `unconfiguredStacks.useApprovedGeneric` contrôle explicitement le maintien des génériques. Les contraintes obligatoires structurées `constraints` sont comparées par clé/valeur ; les contradictions en prose nécessitent la revue de publication.

Chaque tâche déclarée porte une action serveur parmi `CHAT`, `CODE_GENERATION`, `SONAR_REMEDIATION`, `XRAY_REMEDIATION`, `MR_DESCRIPTION`, `AGENT_RUN`. Le changement de tâche ne confère aucun droit. Les profils sont détectés sur l'arbre GitLab ; les profils locaux peuvent ajouter une nouvelle stack, jamais supprimer les profils serveur.

## Starters organisationnels

Configurer dans l'environnement du processus VS Code `ODS_STARTER_DIRECTORY` et `ODS_STARTER_PUBLIC_KEY`. Le répertoire contient `starters.json`, `starters.sig` et les archives ZIP référencées. Le manifeste version 1 contient `version`, `issuedAt`, `expiresAt`, `profiles`. Chaque profil `JAVA`, `ANGULAR`, `PYTHON` ou `DOTNET` contient `mode` (`REPLACE` ou `OVERLAY`), `archive`, `sha256`, `replacePaths`. Toute collision non déclarée est refusée. Les variables textuelles `{{projectName}}` et `{{pythonModule}}` sont remplacées sans évaluation de code. Les projets existants ne sont jamais régénérés automatiquement.

## Qualité, sécurité et exploitation

Les mappings `ods.sonar-project-<ID>` et `ods.jfrog-build-<ID>` sont administratifs ; voir le YAML d'exemple. Le connecteur Xray utilise la lecture v2 de synthèse d'un build. Vérifier la compatibilité et les licences des instances cibles. Un rapport historique ne prouve pas la qualité du commit local.

Le runner est un projet GitLab dédié (`ODS_RUNNER_PROJECT_ID`, `ODS_RUNNER_REF`, `ODS_RUNNER_TOKEN`) : la gateway lance et suit un pipeline réel avec une révision cible immuable. Son isolation, ses images et ses droits sont configurés par l'organisation ; voir [RUNNER.md](RUNNER.md).

En production, activer le profil `production`, configurer PostgreSQL et TLS, sauvegarder la base ainsi que les clés publiques approuvées et les bundles. Ne jamais exposer H2 ou une console d'administration. Restaurer la base avant de réactiver l'IA pour conserver les quotas et watermarks anti-rejeu.

## Sessions, quotas et reprise

Configurer `ODS_DATA_KEY_BASE64` avec 32 octets aléatoires encodés Base64, conservés dans le coffre du déploiement. Les contenus de jobs et checkpoints sont chiffrés en AES-GCM, liés à leur identité, avec nonce neuf. La clé est obligatoire en production. Sans clé explicite en local, une clé éphémère est utilisée : les anciens contenus deviennent illisibles après redémarrage. Sauvegarder et restaurer la clé avec la base via le coffre ; ne jamais la joindre au package.

Les checkpoints conversationnels expirent après 30 minutes ; les résultats détaillés après un jour ; les événements d'audit après 90 jours. `DELETE /api/v1/ai/sessions/{id}` permet au propriétaire de supprimer sa conversation. Les réservations de quota dont la consommation reste incertaine ne sont pas libérées automatiquement.

Un bail de cinq minutes protège les jobs. Un worker abandonné passe à `SUSPENDED`, sans réexécuter d'effet. Le bouton **Reprendre** revalide les droits et recharge le checkpoint chiffré ; les résultats d'outils interrompus sont marqués incertains et doivent être réinspectés. Les mises à jour utilisent une révision optimiste et les nouveaux tours une revendication atomique en base. Le plafond commun est de deux tâches actives et soixante tours par minute et par sujet.

Le flux SSE affiche les fragments de texte disponibles ; le job persistant fait autorité pour les outils, l'état final et l'usage mesuré. Après une coupure du flux de présentation, le client continue à suivre le résultat du job.

## Catalogue Mistral et rotation de confiance

Le champ signé `models` contient les couples `alias`, `provider` (`MISTRAL` uniquement), `model` (identifiant exact approuvé) et `tools` (qualification des appels d’outils). `ODS_MISTRAL_MODEL` doit correspondre au modèle signé de `ODS_MODEL_ALIAS`. L’exemple utilise volontairement `REPLACE_WITH_APPROVED_MISTRAL_ID`, à remplacer après décision organisationnelle. `monthlyUserTokenQuota` borne chaque utilisateur en complément de `monthlyTokenQuota` ; absent, il reprend le plafond projet. Les deux réservations sont atomiques et la réconciliation est unique.

Une valeur de clé publique peut être une clé DER Base64 seule ou un objet JSON administré de forme `{"keys":[{"keyId":"rotation-1","publicKey":"<DER_BASE64>","notBefore":"<INSTANT_UTC>","notAfter":"<INSTANT_UTC>","revoked":false}]}`. Au maximum huit clés sont acceptées. Les clés révoquées, expirées ou futures ne vérifient aucun document. Le trousseau est une configuration de confiance du déploiement : ne jamais le charger depuis le document à vérifier. Déployer un chevauchement, signer avec la nouvelle clé, puis révoquer l’ancienne après migration ; qualifier ce changement avec les caches et les sauvegardes.
