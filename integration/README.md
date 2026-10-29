# Personnaliser HMSile dev IA et installer le VSIX

Ce dossier prépare la personnalisation de la version **0.3.0** avec le mécanisme de build existant. Aucun code applicatif n'est modifié. Les URL `*.hmsile.example`, comptes, IDs et watch sont fictifs ; les secrets sont des placeholders à remplacer dans une copie privée.

**État de cette livraison : aucun build, test ou installation n'a été exécuté pour ce profil.** Le dossier `suiteInstall/` exceptionnellement commité sur `hm/integration` reste la livraison **organisation-demo 0.3.0**, issue du commit `e89936aabe89520d5fa45ad40e9f09e9a3ce6675`, comme l'indique son `BUILD.json`. Son VSIX n'est pas personnalisé HMSile. Les commandes suivantes sont à exécuter ultérieurement par l'administrateur.

## 1. Repérer les fichiers

| Fichier | Rôle |
|---|---|
| `organisation.json` | Identité de l'extension et fichiers utilisés pendant le packaging. |
| `gateway.env.example` | URL, choix de transport, comptes de service, secrets à fournir et gouvernance. |
| `application-organisation.properties` | Correspondance entre IDs de projets GitLab et clés de projets Sonar. |
| `.gitignore` | Exclut `integration/local/`, réservé à la copie privée de l'administrateur. |

Les chemins dans `envFiles` et `files` sont relatifs au dossier du profil. Ils doivent rester dans ce dossier. Les fichiers env sont chargés dans l'ordre ; `environment` prend ensuite priorité. Ici, `environment` est vide : tous les paramètres de service sont regroupés dans le fichier env.

## 2. Préparer une copie privée

Ouvrir **Invite de commandes (`cmd.exe`)**, à la racine du projet. Pour la première préparation uniquement :

```bat
mkdir integration\local
copy integration\organisation.json integration\local\organisation.json
copy integration\gateway.env.example integration\local\gateway.env.example
copy integration\application-organisation.properties integration\local\application-organisation.properties
```

Si `integration/local/` existe déjà, conserver son contenu et éditer les fichiers existants : ne pas recopier les modèles par-dessus une configuration renseignée. Éditer ensuite uniquement cette copie privée. Les modèles versionnés doivent conserver leurs placeholders.

Un fichier env contient une valeur littérale par ligne, `CLE=valeur`, sans `set`, guillemets ni commentaire en fin de ligne. Les caractères `=` dans une valeur sont conservés. Ne pas exécuter ce fichier comme un script. Une valeur complète `${VARIABLE_BUILD}` permet aussi au packaging de lire une variable d'environnement fournie explicitement par le système de build ; son absence provoque un échec. `%VARIABLE%` n'est pas cette syntaxe.

## 3. Choisir le nom de l'extension

Dans `integration/local/organisation.json` :

| Champ | Valeur proposée et effet |
|---|---|
| `schemaVersion` | `1`, format de profil pris en charge. |
| `id` | `hmsile-integration`, identifiant du profil dans les métadonnées du package. |
| `extension.displayName` | **HMSile dev IA**, nom affiché de l'extension, de sa vue et de ses commandes. |
| `extension.name` | `hmsile-dev-ia`, nom technique et préfixe du fichier VSIX. |
| `extension.publisher` | `hmsile`, identifiant technique proposé ; l'ID installé sera `hmsile.hmsile-dev-ia`. Cela ne crée pas un compte Marketplace. |
| `gatewayUrl` | Champ de compatibilité avec le schéma existant. Garder la valeur loopback du modèle : il ne redirige pas le gateway embarqué, dont le port est dynamique. |

Conserver le même couple `publisher` / `name` pour les mises à jour. La version vient du projet, actuellement `0.3.0`, et ne se définit pas dans ce profil.

## 4. Configurer Mistral et Vibe

Dans `integration/local/gateway.env.example` :

| Variable | Valeur à fournir / utilisation |
|---|---|
| `ODS_LLM_TRANSPORT` | `VIBE_CLI` par défaut ; `API` pour les appels HTTP Mistral directs. |
| `ODS_MISTRAL_URL` | Base HTTPS réelle de l'API directe, sans `/v1/chat/completions`. Le modèle utilise `https://mistral.hmsile.example`. |
| `ODS_VIBE_URL` | Base HTTPS de l'API utilisée par Vibe, généralement terminée par `/v1`. Ce n'est pas l'URL de téléchargement du CLI. |
| `ODS_MISTRAL_MODEL` | ID exact du modèle autorisé par l'organisation et disponible sur son endpoint. Remplacer `<APPROVED_MISTRAL_MODEL_ID>` ; aucun nom de modèle valide n'est supposé. |
| `ODS_MODEL_ALIAS` | `hmsile-coding-approved`, alias à reprendre dans la politique signée. |
| `ODS_VIBE_AUTH` | `API_KEY` dans le modèle ; `CLI_TOKEN` pour l'autre credential accepté par le backend. |
| `ODS_MISTRAL_API_KEY` | Clé Mistral de l'organisation, utilisée par Vibe en mode `API_KEY` et par l'API directe. |
| `ODS_VIBE_TOKEN` | Vide en mode `API_KEY`. Renseigner le credential en mode `CLI_TOKEN`. |

Choix possibles :

- **Vibe + clé API** : conserver `VIBE_CLI`, `API_KEY`, renseigner `ODS_MISTRAL_API_KEY`, laisser `ODS_VIBE_TOKEN` vide.
- **Vibe + token CLI** : conserver `VIBE_CLI`, choisir `CLI_TOKEN`, renseigner `ODS_VIBE_TOKEN`, vider `ODS_MISTRAL_API_KEY` si inutilisée.
- **API directe** : choisir `API`, renseigner `ODS_MISTRAL_URL`, le modèle et `ODS_MISTRAL_API_KEY`, laisser `ODS_VIBE_TOKEN` vide.

Le token CLI doit être accepté par l'endpoint Mistral configuré ; un cookie ou token SSO arbitraire ne convient pas. En mode Vibe, le build embarque Vibe **3.0.3** et son adaptateur utilisant le backend officiel. Il ne nécessite pas une configuration personnelle `~/.vibe` et ne lance pas l'interface interactive du CLI. Python 3.12 reste nécessaire sur le poste cible.

## 5. Configurer JFrog Xray

| Variable | Valeur à fournir / utilisation |
|---|---|
| `ODS_JFROG_URL` | URL HTTPS de la plateforme JFrog ; exemple fictif `https://jfrog.hmsile.example`. |
| `ODS_JFROG_AUTH` | `BASIC` dans ce modèle, ou `TOKEN`. |
| `ODS_JFROG_USERNAME` | Compte de service réel ; exemple `svc-hmsile-xray`. Requis dans les deux modes. |
| `ODS_JFROG_PASSWORD` | Mot de passe du compte pour `BASIC` ; vide pour `TOKEN`. |
| `ODS_JFROG_TOKEN` | Access token du compte pour `TOKEN` ; vide pour `BASIC`. |
| `ODS_JFROG_WATCH` | Nom exact du watch existant. `hmsile-dev-watch` est fictif ; le build ne crée pas ce watch. |
| `ODS_JFROG_PROJECT_KEY` | Facultatif : clé projet JFrog envoyée au Graph Scan ; laisser vide si non utilisée. |

En mode `TOKEN`, l'authentification HTTP utilise un Bearer token. Le compte doit être autorisé à lancer un Graph Scan avec le watch choisi. Security permet de générer/exporter le SBOM puis de demander le rapport Xray, sans attendre un commit. Les corrections LLM nécessitent aussi les droits IA de la politique.

## 6. Configurer Sonar

| Variable / fichier | Valeur à fournir / utilisation |
|---|---|
| `ODS_SONAR_URL` | URL HTTPS réelle de SonarQube ; exemple `https://sonar.hmsile.example`. |
| `ODS_SONAR_USERNAME` | Nom administratif du compte associé au token ; exemple `svc-hmsile-sonar`. |
| `ODS_SONAR_TOKEN` | Token de ce compte avec droits d'analyse **et** de lecture des résultats. Le nom d'utilisateur ne remplace pas le token. |
| `application-organisation.properties` | Une ligne `ods.sonar-project-<ID_GITLAB>=<CLE_SONAR>` par projet applicatif. |

Exemple fictif fourni : `ods.sonar-project-1042=hmsile-application-demo`. Remplacer `1042` par l'ID numérique du projet applicatif GitLab, et la valeur par sa clé Sonar. Ce n'est pas l'ID du dépôt de gouvernance. Quality → **Analyser** travaille sur les fichiers enregistrés, même non commités. Le projet doit aussi posséder la configuration nécessaire à son scanner.

## 7. Configurer GitLab et les autorisations IA

| Information | Valeur à fournir / utilisation |
|---|---|
| `ODS_GITLAB_URL` | Origine HTTPS GitLab réelle. Son hôte doit correspondre au remote `origin` du projet ouvert dans VS Code. |
| Username GitLab de service | `svc-hmsile-governance` dans le commentaire du modèle env : inventaire administratif à remplacer. L'application ne possède pas de variable `ODS_GITLAB_USERNAME` ; son API utilise le token. |
| `ODS_GOVERNANCE_TOKEN` | Token du compte de service, capable de lire le dépôt privé de gouvernance ainsi que les métadonnées/fichiers des projets autorisés. |
| `ODS_GOVERNANCE_PROJECT_ID` | ID numérique du dépôt privé de gouvernance. `2001` est un exemple fictif. |
| `ODS_GOVERNANCE_REF` | Branche ou référence protégée contenant les règles ; `main` dans le modèle. |
| `ODS_POLICY_PUBLIC_KEY` | Clé **publique** Ed25519 au format DER encodé Base64, correspondant au signataire de la politique. La clé privée reste hors du VSIX. |

Préparer le dépôt de gouvernance à partir de [l'exemple de politique](../examples/ai-project-policy.json). Adapter `gitlabInstance`, les IDs des projets, les dates de validité, les quotas, le modèle réel et son alias `hmsile-coding-approved`. Le modèle doit correspondre exactement à `ODS_MISTRAL_MODEL` et être associé au fournisseur `MISTRAL`.

Pour les fonctions IA locales, autoriser les projets voulus avec `aiEnabled: true`, `contextMode: LOCAL_GOVERNED` et leurs actions. Les boutons GitFlow nécessitent les droits distincts `CHANGELOG` et `RELEASE_NOTES` ; les corrections nécessitent `SONAR_REMEDIATION` ou `XRAY_REMEDIATION`. Faire signer et publier `ai-project-policy.json` et `ai-project-policy.sig` à la racine du dépôt de gouvernance par le processus organisationnel existant. L'exemple n'est pas une politique signée utilisable tel quel.

**Aucun SSO à configurer.** Le développeur doit déjà pouvoir lire le dépôt avec son identité Git/IDE. Ce contrôle précède le démarrage du gateway. Les opérations GitFlow utilisent les identifiants du développeur ; le token de service ci-dessus ne les remplace pas. Pour créer une MR, un token API GitLab utilisateur doit déjà être disponible dans le fournisseur d'authentification IDE ou Git Credential Manager ; SSH seul ne fournit pas ce token API.

## 8. Autres réglages

| Variable | Consigne |
|---|---|
| `ODS_DATA_KEY_BASE64` | Laisser vide : une clé stable de chiffrement des sessions est générée dans SecretStorage. Si l'organisation impose sa clé, fournir exactement 32 octets encodés Base64 dans la copie privée. |
| `ODS_PROMPT_DIRECTORY` | Laisser vide pour utiliser les prompts livrés. Un bundle organisationnel externe exige un répertoire réellement disponible sur chaque poste cible et un déploiement administré. |
| `ODS_PROMPT_PUBLIC_KEY` | Laisser vide sans bundle externe ; sinon clé publique de vérification de ce bundle signé. |
| `ODS_BIND_ADDRESS`, `ODS_GATEWAY_PORT`, `SPRING_PROFILES_ACTIVE` | Conserver les valeurs du modèle. Le lancement embarqué impose loopback, port libre et profil `local`. |

Le VSIX gère son stockage H2 local ; aucune base PostgreSQL ni configuration OIDC/runner n'est nécessaire à ce parcours. Ne pas ajouter les variables réservées au lancement : `ODS_LOCAL_TOKEN`, `ODS_EMBEDDED`, `ODS_RUNTIME_DIRECTORY`, `ODS_PYTHON_COMMAND`, `ODS_WORKSPACE`, `ODS_REPOSITORY`.

## 9. Construire ultérieurement le VSIX personnalisé

Installer sur la machine de build **JDK 21**, **Node.js 24 avec npm**, **Python 3.12** et **Git**. `java`, `javac`, `node`, `npm`, `python` et `git` doivent être accessibles. Le wrapper télécharge Maven ; les dépendances npm/Python et Chromium nécessitent aussi l'accès aux registres configurés. Construire sur le système et l'architecture cibles : les dépendances Vibe incluent du code natif.

Après remplacement de toutes les valeurs fictives nécessaires, à la racine du projet dans `cmd.exe` :

```bat
script.bat "integration\local\organisation.json"
```

**Cette commande exécutera la compilation et les tests complets**, puis le packaging. Elle n'a pas été exécutée pour la présente préparation. Ne pas installer un artefact provenant d'une commande en échec.

Avec la version actuelle, les sorties attendues après réussite sont :

```text
.artifacts/hmsile-dev-ia-0.3.0.vsix
suiteInstall/bin/hmsile-dev-ia-0.3.0.vsix
suiteInstall/BUILD.json
suiteInstall/VERSION
suiteInstall/SHA256SUMS
suiteInstall-0.3.0.zip
```

Le `profileId` du nouveau `BUILD.json` doit être `hmsile-integration`. Le build conserve une copie de l'ancienne livraison dans `.artifacts/`. Pour produire uniquement le VSIX, avec les mêmes validations :

```bat
python tools\build.py --task Vsix --organisation-profile "integration\local\organisation.json"
```

Utiliser ces commandes pour le profil HMSile : le wrapper actuel `generate-vsix.bat` ne transmet pas d'argument de profil à Python. `build.bat` seul ne produit pas le VSIX personnalisé. Aucun appel PowerShell n'est nécessaire.

Le fichier livré `suiteInstall/config/organisation.json` contient des **métadonnées**, pas le profil d'entrée réutilisable. Le fichier livré `gateway.env` masque les secrets ; le VSIX privé contient les valeurs compilées. Changer ces fichiers de livraison ne reconfigure pas le VSIX.

## 10. Installer sur le poste développeur

1. Installer **VS Code Desktop 1.95.0 ou supérieur dans la série 1.x** (dont 1.95.2), **Java 21**, **Python 3.12** et **Git**. Utiliser le VSIX de la plateforme du poste.
2. Désactiver l'ancienne variante ODS dans le profil VS Code concerné : les variantes partagent les commandes `ods.*`. Une identité `hmsile.hmsile-dev-ia` n'est pas une mise à jour automatique de `ods-internal.organisation-development-standard`.
3. Dans Extensions, menu `…` → **Installer à partir de VSIX…**, sélectionner le nouveau `hmsile-dev-ia-0.3.0.vsix`. Autre possibilité, depuis la racine du projet après le build réussi :

   ```bat
   code --install-extension "suiteInstall\bin\hmsile-dev-ia-0.3.0.vsix" --force
   ```

   Sur un autre poste, adapter uniquement le chemin vers le fichier transféré. Le script existant `suiteInstall/scripts/install-extension.bat` cible le nom historique `organisation-development-standard-*` : utiliser l'installation explicite ci-dessus pour HMSile.
4. Recharger la fenêtre si VS Code le demande, puis ouvrir un dépôt Git autorisé et approuver le workspace. Vérifier que l'identité Git existante peut accéder à son `origin`.
5. Ouvrir **HMSile dev IA**. Le contrôle Git puis le démarrage du gateway sont automatiques ; ne pas lancer manuellement un second gateway. Les services de l'organisation doivent être joignables et la politique valide.
6. Installer les outils de la stack pour Quality/Security : Maven pour Java, Node/npm pour Angular, `cyclonedx-py` pour Python, SDK .NET et `dotnet-CycloneDX` pour .NET ; Sonar nécessite aussi son scanner adapté. Voir les prérequis exacts dans [INSTALLATION](../docs/INSTALLATION.md).

L'administrateur qualifie ensuite les services réels : envoi IA, génération SBOM, rapport Xray et analyse Sonar. Un build réussi ne prouve pas les droits réseau ni les permissions de ces services. Les comptes et URL organisationnels ne sont pas éditables dans l'interface du développeur. La fermeture de la fenêtre ou la désactivation de l'extension arrête ses processus ; masquer la vue ne désactive pas l'extension.

## 11. Livraison privée et maintenance

Les secrets embarqués restent extractibles, conformément au mode autonome retenu. Distribuer le VSIX personnalisé par le canal privé de l'organisation. Une rotation de secret, un changement d'URL ou de modèle nécessite un nouveau build avec la copie privée, puis une réinstallation.

L'exception Git de `hm/integration` concerne uniquement les modèles fictifs et l'instantané de démonstration déjà présent. **Après un build avec de vrais secrets, ne pas commiter le nouveau `suiteInstall` ni ses archives.** Ce dossier est suivi sur cette branche : `.gitignore` ne protège pas les fichiers déjà suivis. `integration/local/` reste ignoré et ne doit jamais être ajouté avec `git add -f`.

En cas de refus Git, corriger l'accès du développeur au remote. En cas de refus IA, vérifier la signature, les dates, les IDs, le modèle et les actions de la politique. Si Xray ou Sonar échoue, vérifier les droits du compte, le watch/mapping et les outils de stack. Les domaines `.example` et placeholders de ce dossier ne permettent aucune connexion réelle.
