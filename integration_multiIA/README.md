# Intégration multi-IA — ODS 0.8.0

Ce dossier configure Mistral et OpenAI pour un même projet en modes Question, Modification et Agent. Les fichiers fournis sont des exemples sans secrets réels.

## Configuration privée

Copier `organisation.json`, `gateway.env.example`, `application-organisation.properties` et `ai-project-policy.json` dans `integration_multiIA/local/`. Créer ce dossier uniquement s’il n’existe pas et conserver toute configuration déjà renseignée. `local/` est ignoré par Git.

Dans le fichier env privé, remplacer les URL GitLab/Sonar/Xray, l’ID et la référence du dépôt de gouvernance, les credentials et la clé publique Ed25519. Adapter le mapping Sonar. Conserver le registre OpenAI et `ODS_LLM_TRANSPORT=VIBE_CLI`.

Fournir `MISTRAL_MODEL`, `MISTRAL_API_KEY` et `OPENAI_API_KEY` dans l’environnement du processus de build par le mécanisme privé habituel. Les références `${VARIABLE}` sont résolues pendant le packaging ; une variable absente provoque un échec. Ne pas exécuter le fichier env comme un script.

## Politique à deux modèles

Dans la copie privée de `ai-project-policy.json` :

- Remplacer l’instance GitLab, le projet fictif 1000, la clé Sonar et les deux IDs de modèles par les valeurs approuvées. Le modèle Mistral doit correspondre exactement à `MISTRAL_MODEL`.
- Conserver les deux fournisseurs et les deux alias dans `allowedModelAliases`. Le défaut `modelAlias` doit appartenir à cette liste.
- Restreindre les actions, agents et quotas aux droits réels. Adapter les plafonds aux limites du modèle exact. Les choix utilisables dans les trois modes nécessitent `tools: true`.
- Augmenter `policyVersion` par rapport à la politique publiée et renouveler les dates avec une validité maximale de 24 heures. Les dates fournies sont illustratives.

Faire signer les octets finaux avec le processus Ed25519 existant, puis publier `ai-project-policy.json` et `ai-project-policy.sig` à la racine du dépôt de gouvernance configuré. La clé privée reste hors du profil et du VSIX. Le JSON local est un exemple non signé ; seule la politique signée accorde les droits.

Un troisième modèle peut être ajouté au catalogue et à `allowedModelAliases` (maximum trois), avec un alias distinct et un fournisseur déjà configuré. Un nouveau fournisseur exige sa configuration et sa qualification.

Voir [la configuration actuelle](../docs/CONFIGURATION.md) et [le guide des services](../integration/README.md). Le guide historique des services utilise la version 0.3.0 ; les commandes et versions ci-dessous concernent ce profil 0.8.0.

## Construction et installation

Prérequis : JDK 21, Node.js 24/npm, Python 3.12 et Git, avec accès aux registres de dépendances. Depuis la racine, après remplacement des placeholders :

```bat
py -3.12 tools\build.py --task Vsix --organisation-profile integration_multiIA\local\organisation.json
```

La commande compile et teste avant de produire le VSIX. Installer uniquement après succès :

```bat
code --install-extension .artifacts\organisation-development-standard-0.8.0.vsix --force
```

Le profil conserve l’identité ODS existante : cette installation la met à jour. Le VSIX personnalisé contient les credentials résolus, extractibles dans le mode autonome ; le distribuer uniquement par le canal privé de l’organisation.

Pour produire ultérieurement le package autonome, utiliser `--task Package` au lieu de `--task Vsix`. `suiteInstall/` et `suiteInstall-0.8.0.zip` restent ignorés par Git. Ces commandes ne créent ni tag ni release.

## Validation sur le poste cible

Ouvrir un dépôt GitLab autorisé, vérifier son accès Git et connecter ODS. Le sélecteur doit proposer les deux modèles avec leur fournisseur et leur identité exacte. Tester les trois modes selon les permissions, les quotas et le refus d’un projet non autorisé. Changer de modèle ouvre une nouvelle conversation ; le choix est verrouillé pendant une tâche.

Les appels réels, les droits réseau et l’installation de ce profil restent à qualifier sur l’environnement cible. Les tests automatiques du projet utilisent des fournisseurs simulés.

## Validation de cette préparation — 12 novembre 2026

`py -3.12 tools/build.py --task Build` : SUCCESS (code retour 0). Tests : 78 Java, 40 Python, 74 Node et 18 scénarios Chromium réussis. Compilation TypeScript, lint et contrôles documentaires réussis. Les deux JAR 0.8.0 ont été générés ; version moteur, démarrage gateway, santé et refus anonyme vérifiés par le smoke existant.

Chargement du profil par `tools/organisation_profile.py validate` : SUCCESS. Cohérence des deux alias, fournisseurs, défaut et capacités outils : vérifiée. Exclusion de `local/` et des livrables : vérifiée. Aucun secret évident ni chemin privé absolu détecté dans les fichiers concernés.

VSIX personnalisé, package suiteInstall, installation et appels fournisseurs réels : NON EXÉCUTÉS pour ce profil, qui nécessite les paramètres privés de l’organisation. Aucun tag ni release créé ; version applicative inchangée.
