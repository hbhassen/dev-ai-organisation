# Mécanique — assistant de gouvernance ODS

Version **1.0.0**. Mini-projet indépendant de l'extension VS Code.

Mécanique aide un administrateur à ajouter plusieurs projets, modifier les propriétés d'un projet existant, ajouter des modèles au catalogue, ou simplement renouveler une politique. Il vérifie la signature existante, propose les modifications, incrémente `policyVersion` une fois, renouvelle les dates pour exactement 24 heures, signe puis publie les deux fichiers dans un seul commit Git.

## Vos autres fichiers restent inchangés

L'outil travaille dans **un nouveau répertoire temporaire**, pas dans votre clone habituel. Le sparse checkout matérialise uniquement :

```text
ai-project-policy.json
ai-project-policy.sig
```

Ces fichiers doivent être à la racine du dépôt de gouvernance. Le commit est refusé s'il ne modifie pas exactement ces deux fichiers. Aucun `git add .`, aucun push forcé, aucun commit des clés ou d'un fichier `.gitattributes`. Les réglages de fins de ligne sont stockés uniquement dans `.git/info/attributes` du checkout temporaire.

Les objets et métadonnées `.git` sont nécessaires au fonctionnement de Git. Le fetch utilise une profondeur de 1 et demande `blob:none` ; si le serveur ne supporte pas ce filtre, il peut transférer d'autres objets, mais **les autres fichiers ne sont ni checkoutés, ni édités, ni inclus dans le commit**.

## Prérequis

- Python **3.11 ou supérieur**, et Git accessibles dans le terminal.
- Accès Git existant à votre GitLab : Git Credential Manager pour HTTPS, ou clé/agent SSH configuré. L'outil n'ouvre pas de demande de token et n'accepte pas de credentials dans l'URL.
- Droit de lecture **et de push** sur la branche de gouvernance choisie. Si la branche est protégée, utiliser un compte autorisé ; l'outil ne contourne pas sa protection et ne crée pas de MR.
- Les deux fichiers existent déjà et leur signature est valide. Une politique expirée peut être renouvelée ; un fichier `.sig` absent ou invalide est refusé.
- La clé privée Ed25519 correspondante et le fichier de clé publique approuvé par les VSIX.
- L'heure du poste est synchronisée.

Le compte utilisé ici publie la politique : il peut être distinct du compte de service en lecture seule utilisé par l'extension. Aucune installation de Java, Maven, Node, Docker ou de l'extension n'est nécessaire.

## Installation sur Windows

Depuis le dossier `mecanique`, dans PowerShell :

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

Cela installe la dépendance de signature `cryptography` dans un environnement propre à cet outil. Les exemples suivants utilisent cet environnement sans imposer son activation.

Sous Linux/macOS, remplacer `.\.venv\Scripts\python.exe` par `.venv/bin/python`.

## Lancer l'assistant

Adaptez les valeurs d'exemple à votre organisation, puis exécutez :

```powershell
.\.venv\Scripts\python.exe run.py edit `
  --repository "https://gitlab.organisation.example/equipe/gouvernance.git" `
  --branch "main" `
  --gitlab-url "https://gitlab.organisation.example" `
  --private-key "<DOSSIER_PRIVE>\signing.key" `
  --public-key "<DOSSIER_PRIVE>\public-key.txt"
```

Remplacer `<DOSSIER_PRIVE>` : c'est un placeholder, pas un répertoire créé automatiquement. La forme SSH `git@gitlab.organisation.example:equipe/gouvernance.git` est également acceptée. Un chemin vers un dépôt bare local permet une recette sans réseau.

| Argument | Valeur attendue |
|---|---|
| `--repository` | URL du **dépôt de gouvernance**, pas du projet à autoriser. |
| `--branch` | Branche existante correspondant à `ODS_GOVERNANCE_REF`. Défaut : `main`. Une branche est requise, pas un tag. |
| `--gitlab-url` | Valeur exacte de `ODS_GITLAB_URL` du profil, identique à `gitlabInstance`. Respecter port et éventuel suffixe. |
| `--private-key` | Fichier Base64 DER PKCS8 Ed25519 généré par `GovernanceMain` ou Mécanique. Jamais ajouté à Git. |
| `--public-key` | Fichier Base64 DER/X.509 Ed25519 contenant la clé approuvée via `ODS_POLICY_PUBLIC_KEY`. |
| `--author-name`, `--author-email` | Facultatifs : identité du commit ; sinon Git utilise sa configuration existante. Ne modifie pas la configuration Git globale. |

Le mode de rotation `ODS_POLICY_PUBLIC_KEY` contenant un objet JSON de plusieurs clés n'est pas interprété ici : fournir la clé simple associée à la signature existante et à la clé privée. La rotation de clés et l'initialisation d'un dépôt vide ne font pas partie de cet assistant.

## Parcours interactif

Après vérification des fichiers et de la signature, le menu propose :

```text
1 Ajouter un projet
2 Modifier un projet
3 Ajouter un modèle
4 Voir le brouillon
5 Publier / renouveler 24h
0 Annuler
```

Vous pouvez effectuer plusieurs actions `1`, `2` et `3` avant de publier. Les changements restent en mémoire. **Entrée conserve la valeur affichée** ; pour les propriétés facultatives, `-` signifie supprimer le champ ou vider la liste, selon l'explication affichée. Une saisie invalide repropose la question. `Ctrl+C` ou `0` annule avant publication.

### Ajouter ou modifier un projet

Chaque propriété possède une question, son rôle et les valeurs possibles :

| Propriété | Rôle et valeurs |
|---|---|
| `projectId` | ID numérique positif du projet GitLab ; unique. Conservé lors d'une modification. |
| `aiEnabled` | Oui/non. Non impose `allowedActions: []` et n'interdit pas les analyses hors IA. |
| `allowedActions` | Liste séparée par virgules. `CHAT`, `CODE_GENERATION`, `SONAR_REMEDIATION`, `XRAY_REMEDIATION`, `MR_DESCRIPTION`, `AGENT_RUN`, `CHANGELOG`, `RELEASE_NOTES`. `-` = aucune. Chaque action est expliquée à l'écran. |
| `modelAlias` | Un des alias du catalogue signé. Pour l'utilisation effective, il doit correspondre à `ODS_MODEL_ALIAS` de l'extension. |
| `monthlyTokenQuota` | Entier >= 0, plafond mensuel du projet. 0 ne signifie pas illimité. |
| `monthlyUserTokenQuota` | Entier >= 0 ou `-` pour supprimer le champ. Ne remplace pas le plafond du projet. |
| `allowedAgents` | Identifiants séparés par virgules, par exemple `implement-requirement`, ou `-`. Le droit `AGENT_RUN` reste distinct. |
| `contextMode` | `LOCAL_GOVERNED` : sources locales filtrées ; `SERVER_VERIFIED` : sources GitLab sur commit immuable. |
| `allowFreeText` | Oui/non. Les prompts automatiques de correction de l'extension actuelle nécessitent également ce droit. Oui ne supprime aucune limite de taille de contexte. |
| `sonarProjectKey` | Clé Sonar exacte, facultative. 1..400 caractères lettres ASCII, chiffres, `_`, `.`, `:`, `-`, non exclusivement numériques. `-` retire le mapping signé et laisse le repli properties. Nécessite ODS **0.6.0 ou supérieur**. |

L'outil ne crée pas le projet GitLab ou Sonar et ne vérifie pas leurs permissions à votre place. Il ajoute seulement la règle de gouvernance. Les quotas et droits ne sont jamais augmentés automatiquement pour rendre une configuration fonctionnelle.

### Ajouter un modèle

L'assistant demande `alias`, `provider`, `model`, `tools` :

- L'alias est unique ; les alias déjà utilisés ne sont pas renommés.
- Le seul provider accepté par le produit actuel est `MISTRAL`.
- `model` est l'identifiant exact de l'API, pas un nom commercial. L'outil n'appelle pas l'API Mistral pour le deviner.
- `tools` indique la qualification pour les appels d'outils ; l'extension la contrôle notamment pour `AGENT_RUN`.

**Ajouter un modèle dans le catalogue ne change pas le modèle embarqué dans les VSIX.** Le modèle et l'alias du profil doivent rester cohérents avec la règle choisie. Le mapping Sonar dynamique évite un rebuild pour l'ajout d'un projet ; il ne rend pas toute la configuration du provider dynamique.

### Publier

Choisir `5`, même sans autre modification, renouvelle la politique. L'outil prépare :

1. `policyVersion = ancienne version + 1`, une seule fois pour l'ensemble des changements.
2. `issuedAt = maintenant UTC` et `expiresAt = issuedAt + 24 heures` exactement.
3. Le diff complet du JSON avant/après.
4. Une demande explicite de confirmation : taper **`PUBLIER`**. Entrée revient au menu.
5. La signature Ed25519 des octets UTF-8 finaux, puis un contrôle de cette signature.
6. Un commit limité au JSON et au SIG, suivi d'un push normal sur la branche choisie.
7. Une vérification que la tête distante correspond au commit publié.

Ne modifiez pas le JSON séparément après la signature. La durée maximale de 24h vient de l'extension actuelle : une politique doit donc être renouvelée régulièrement. **Aucun renouvellement planifié automatique n'est installé par cet outil.**

Après publication, attendre jusqu'à 60 secondes pour le cache de politique puis actualiser l'extension, ou la reconnecter. Un VSIX ancien ne reconnaît pas `sonarProjectKey` ; installer d'abord une version compatible.

## Gestion des clés

Réutilisez la paire approuvée existante. **Ne générez pas une nouvelle clé pour résoudre une simple expiration.** Si vous initialisez séparément une nouvelle organisation :

```powershell
.\.venv\Scripts\python.exe run.py keys "<NOUVEAU_DOSSIER_PRIVE>"
```

Les fichiers sont `signing.key` (privé) et `public-key.txt` (public). Une clé existante n'est jamais écrasée. Le format est identique à celui attendu par le vérificateur Java. Protéger l'accès au dossier avec les permissions du poste, particulièrement sous Windows. La clé publique doit être approuvée dans le profil du VSIX ; une nouvelle paire seule ne rend pas une politique existante vérifiable.

## Erreurs et reprise

| Message / situation | Action |
|---|---|
| Deux fichiers réguliers requis | Ajouter les vrais JSON et SIG à la racine de la bonne branche. Aucun fichier vide de substitution. Les liens symboliques sont refusés. |
| Signature invalide | Vérifier la paire de fichiers et la clé publique ; l'outil ne resignera pas silencieusement un document non authentifié. |
| Clés non associées | Utiliser la clé privée qui correspond à la clé publique approuvée. |
| Git fetch / push refusé | Vérifier Git Credential Manager/SSH, le réseau, le certificat de l'instance, la branche et les droits. Aucun secret ni stderr brut n'est affiché. |
| Identité Git absente | Fournir `--author-name` et `--author-email`, ou configurer votre identité Git. |
| Branche modifiée / push concurrent | Aucun push forcé. Relancer pour relire la nouvelle version et réappliquer les changements. |
| Brouillon signé conservé | Le chemin temporaire de récupération est affiché ; il contient uniquement JSON et SIG. Conserver localement si utile, puis supprimer après reprise. Ne pas pousser aveuglément une ancienne version. |
| Publication non confirmée | Le push a pu réussir puis être suivi d'un autre commit. Vérifier l'historique GitLab avant de relancer. |

Le checkout temporaire est nettoyé après l'opération. Une interruption pendant le push peut laisser un résultat distant incertain ; vérifier GitLab. Un brouillon de récupération n'inclut jamais les clés.

## Build et tests séparés

Depuis `mecanique`, après installation des dépendances :

```powershell
.\.venv\Scripts\python.exe build.py
```

Ou activer l'environnement puis lancer `build.bat`. Ce build effectue uniquement :

- compilation Python du mini-projet ;
- tests unitaires des règles, dates et signatures ;
- tests de l'assistant interactif ;
- tests avec vrais dépôts Git bare **locaux** : checkout de deux fichiers, conservation des autres blobs, commit/push, conflits et annulation ;
- création de `mecanique/suiteInstall/` et `mecanique/suiteInstall-1.0.0.zip`, puis vérification du lancement `--help` depuis la livraison et de l'archive.

Les tests ne contactent ni GitLab d'organisation, ni Sonar, ni Xray, ni Mistral. **Aucun script de l'extension, Maven, npm ou packaging VSIX n'est appelé.** Les tests seuls se lancent aussi ainsi :

```powershell
$env:PYTHONPATH = (Join-Path (Get-Location) "src")
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Pour utiliser la livraison sur un autre poste : extraire le ZIP, entrer dans `suiteInstall`, créer son environnement Python, installer `requirements.txt`, puis lancer `run.py edit` avec les paramètres de cette organisation. Les dépendances ne sont pas vendoriées : leur installation exige un index de paquets accessible. `BUILD.json` indique le commit source et l'état de travail au moment du packaging.


## Validation de cette livraison

Le 3 novembre 2026, `python mecanique/build.py` a réussi sous Windows avec Python 3.11.9 et Git 2.43.0 : **21 tests réussis, aucun échec**. Compilation Python, démarrage depuis `suiteInstall` et intégrité ZIP vérifiés. Les signatures utilisent réellement Ed25519 et les commits/push utilisent de vrais dépôts Git bare temporaires. Les tests incluent les fichiers tiers, les conflits avant et pendant le push, un refus serveur et le parcours interactif multi-projets. Aucun serveur GitLab d'organisation, Docker ou Mistral n'a été utilisé pour cette recette. Aucun build ni test de l'extension n'a été exécuté.
