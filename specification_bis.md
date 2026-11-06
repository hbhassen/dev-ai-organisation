# Spécification bis — Fournisseurs IA supplémentaires via Vibe

**Statut :** amendement intégré à `specification.md` (DEVAI-189 à DEVAI-198).
**Base :** `specification.md` 1.7 et architecture du dépôt au 5 novembre 2026.
**Portée :** providers IA via le runtime Vibe embarqué, et séparation des outils ODS et du JDK du projet utilisateur.

Ce document complète la spécification actuelle et remplace uniquement les règles contradictoires indiquées ci-dessous. Toutes les autres exigences de `specification.md` restent applicables.

## 1. Objectif et décision

ODS DOIT permettre à l’organisation d’approuver et de proposer, en plus des modèles Mistral, des modèles accessibles par les backends génériques réellement pris en charge par la version de Vibe embarquée. **OpenAI est le premier fournisseur supplémentaire à qualifier.** Des fournisseurs compatibles avec les adaptateurs génériques de Vibe pourront être ajoutés après qualification équivalente.

Les appels aux fournisseurs supplémentaires passent par le runtime Vibe embarqué et son backend générique. Le transport Mistral existant reste disponible pour compatibilité. Le comportement initial demeure Mistral seul : aucun autre fournisseur n’est activé sans configuration administrée et autorisation explicite dans la politique signée du projet.

« Via Vibe » désigne le runtime et les adaptateurs Vibe embarqués par ODS. L’extension n’utilise ni le compte personnel Vibe de l’utilisateur, ni son `~/.vibe/config.toml`, ni ses serveurs MCP, outils, hooks ou sessions. Ajouter un modèle dans une configuration Vibe personnelle ne l’ajoute pas à ODS.

## 2. Règles existantes remplacées

Les changements suivants remplacent les prescriptions plus restrictives de `specification.md` :

- **§ 1 — Référentiel et décisions :** remplacer « les fournisseurs IA autres que Mistral restent interdits par défaut » par « tout fournisseur absent du registre administré et de la politique signée reste interdit ; Mistral seul reste activé par défaut ».
- **DEVAI-064 — Modèle par alias administré :** retirer l’exclusivité permanente de `MISTRAL`. La gateway peut résoudre tout fournisseur et modèle préalablement qualifiés, configurés par l’organisation et autorisés dans la politique signée.
- **DEVAI-170 — Mistral API ou Vibe :** conserver le transport Mistral existant et ajouter le routage Vibe générique vers les fournisseurs supplémentaires. Le client ne choisit ni endpoint, ni fournisseur libre, ni identifiant de modèle non approuvé.
- **DEVAI-171 — Distribution Vibe contrôlée :** conserver l’isolation actuelle. Le backend Vibe générique reçoit uniquement la configuration administrée du fournisseur approuvé pour la requête. Le staging concurrent peut utiliser des liens physiques pour le cache Vibe immuable, avec copie normale en repli ; les deux modes doivent préserver les octets, les filtres de fichiers existants et le cache source.
- **DEVAI-176 — Secrets et processus :** appliquer les mêmes règles à toutes les clés et tous les fournisseurs, et non uniquement à Mistral.

L’architecture actuelle confirme le point de départ : `scripts/vibe_bridge.py` construit aujourd’hui un backend `mistral`, `VibeProvider` fournit une configuration et des credentials Mistral, et la validation de la politique impose actuellement `MISTRAL`. Le changement doit lever ces restrictions de façon explicite et vérifiable. Le contrat `ModelProvider` reste le point d’abstraction IA.

## 3. Registre des fournisseurs et des modèles

### DEVAI-189 — Registre administré des fournisseurs

L’organisation DOIT déclarer les fournisseurs utilisables par le runtime Vibe dans sa configuration administrée. Chaque entrée comporte au minimum :

- un identifiant stable, par exemple `MISTRAL` ou `OPENAI` ;
- le backend Vibe (`mistral` ou `generic`) et un `api_style` pris dans la liste supportée par la version embarquée ;
- l’URL de base approuvée et les hôtes de destination autorisés ;
- la référence du credential administré, sans sa valeur ;
- les capacités qualifiées : génération, outils, streaming, images et retour d’usage.

OpenAI utilise le backend générique de Vibe et une clé API OpenAI administrée. Les modèles OpenAI sont appelés avec l’identifiant exact attendu par l’API et l’API style validé avec le runtime embarqué. Les adaptateurs OpenAI Chat Completions et Responses ne sont activés que si leur contrat est vérifié dans la version distribuée.

Les profils Vibe documentent les champs `backend`, `api_base`, `api_style` et `api_key_env_var`, ainsi que les styles génériques OpenAI et OpenAI Responses. La version embarquée du dépôt est actuellement documentée comme Vibe 3.0.3 ; toute prise en charge doit toutefois être établie par les tests de contrat sur l’artefact réellement empaqueté, pas déduite de la documentation d’une version plus récente. [Référence de configuration Vibe](https://docs.mistral.ai/vibe/code/cli/configuration-reference) · [Profils et fournisseurs Vibe](https://docs.mistral.ai/vibe/code/cli/api-keys-profiles)

Les URLs proviennent exclusivement de la configuration administrée. La politique de projet ne peut pas introduire une URL, un en-tête, un proxy ou un endpoint arbitraire. Les hôtes non approuvés, les schémas non TLS hors loopback de test et les redirections vers une destination non autorisée sont refusés.

### DEVAI-190 — Catalogue signé et autorisation par projet

`allowedProviders` dans la politique signée devient une liste des fournisseurs autorisés, et non une liste obligatoirement égale à `["MISTRAL"]`. Chaque entrée `models[]` référence un fournisseur présent dans cette liste ainsi qu’un identifiant de modèle exact. Chaque modèle non-Mistral déclare aussi `maxContextTokens` et `maxOutputTokens` positifs, bornés et cohérents ; une politique Mistral historique sans ces champs conserve ses limites compatibles. Une règle de projet ne peut sélectionner que l’alias d’un modèle présent dans ce catalogue.

Exemple conceptuel de politique, à adapter au schéma strict existant :

```json
{
  "allowedProviders": ["MISTRAL", "OPENAI"],
  "models": [
    { "alias": "mistral-code", "provider": "MISTRAL", "model": "<MODELE_MISTRAL_APPROUVE>", "tools": true },
    { "alias": "openai-code", "provider": "OPENAI", "model": "<ID_MODELE_OPENAI_APPROUVE>", "tools": true, "maxContextTokens": 32768, "maxOutputTokens": 4096 }
  ]
}
```

Le registre de déploiement et la politique signée sont cumulatifs : un fournisseur ou modèle n’est utilisable que s’il est présent dans les deux périmètres applicables. Les modèles inconnus, alias dupliqués, fournisseurs absents de `allowedProviders`, capacités non qualifiées ou signatures invalides sont refusés avant tout appel réseau.

La gateway demeure seule responsable de résoudre l’alias vers le fournisseur, l’identifiant exact, les limites, les capacités et le credential. Les requêtes clientes ne peuvent pas remplacer ces valeurs. Les mises à jour de politique conservent la signature, l’expiration, l’anti-rejeu et les contrôles de cache existants.

## 4. Appel à Vibe et sélection du modèle

### DEVAI-191 — Résolution Vibe sans configuration personnelle

La gateway transmet au bridge Vibe une référence de fournisseur et un modèle déjà résolus à partir du registre administré et de la politique signée. Le bridge construit `ProviderConfig` et `ModelConfig` pour le backend qualifié. Il ne prend jamais l’URL, le style d’API, le nom du fournisseur ou la clé directement depuis le client.

La valeur du credential est injectée uniquement dans le processus Vibe concerné, par le mécanisme privé déjà utilisé par le bridge, puis exposée au backend au moyen de sa variable d’environnement dédiée. Elle ne doit apparaître ni dans les arguments de commande, ni dans la configuration persistante, ni dans le DOM, les journaux, les diagnostics ou les événements d’audit. La clé OpenAI porte une référence distincte de la clé Mistral. Les tests utilisent uniquement des credentials synthétiques.

Chaque requête utilise un environnement Vibe temporaire et isolé. Les valeurs de provider autorisées sont construites depuis la configuration administrée ; aucun fichier Vibe utilisateur ou workspace n’est chargé ou modifié. Les outils ODS restent ceux du contrat typé existant et restent soumis aux autorisations, approbations et contrôles de la gateway.

### DEVAI-192 — Choix des alias autorisés

Quand l’interface permet le choix d’un modèle, elle affiche uniquement les alias autorisés pour le projet courant. Le badge DOIT afficher le fournisseur et l’identifiant exact du modèle résolus par la gateway, jamais un fournisseur fixé dans le code ni l’alias à la place du modèle. Avant réponse, il indique la sélection de la politique ; après réponse, il indique le modèle utilisé par cette réponse. Chaque message assistant conserve sa propre attribution fournisseur/modèle issue du résultat du job, même après actualisation de la politique. Sans métadonnées, l’affichage est neutre ; après refus, déconnexion ou changement de projet, le badge précédent est retiré. Les confirmations de contexte utilisent la destination résolue. Aucun affichage ne contient clé, en-têtes ou URL privée. Si la politique impose un alias, cet alias reste sélectionné et aucun choix client ne peut le remplacer.

Le changement d’alias est réévalué par la gateway à chaque requête. La révocation d’un fournisseur, d’un modèle ou du droit IA prend effet au prochain contrôle de politique et bloque les appels suivants. Aucun repli silencieux vers Mistral, OpenAI ou un autre modèle n’est permis.

Une règle signée avec `aiEnabled: false` conserve un affichage de capacités valide sans `modelAlias`. La gateway ne résout aucun fournisseur pour cette règle et refuse la génération avant tout appel au modèle. Cette compatibilité est vérifiée par un test HTTP sur le contrôleur réel.

## 5. Capacités, consommation et données

### DEVAI-193 — Capacités et contrat de génération

Chaque modèle est activé uniquement pour les capacités effectivement vérifiées : taille de contexte et limite de sortie exactes dans le catalogue signé, outils typés, streaming, vision et usage retourné dans le registre administré. L’estimation d’entrée conservatrice utilise la taille UTF-8 sérialisée et le framing des messages, réserve la sortie puis refuse avant appel si les limites dépassent le contexte déclaré. La sortie demandée est bornée par `maxOutputTokens`. L’estimation est indiquée comme telle ; les compteurs fournisseur restent la mesure de consommation. La réponse du modèle ne peut pas augmenter les capacités. Si une action ODS nécessite une capacité absente, l’appel est refusé avec un état explicite ou l’action est désactivée par la politique.

La gateway normalise les réponses texte, appels d’outils, fins de tour, erreurs, annulation et compteurs d’usage vers les contrats ODS existants. Le streaming conserve les IDs d’événements, la reprise et un événement terminal. Si un adaptateur ou un modèle ne permet pas une capacité requise, il ne peut pas être déclaré compatible avec les parcours qui en dépendent.

Les capacités publiques exposent `provider`, `model`, `modelAlias`, `tools` et `maxOutputTokens` pour les projets IA autorisés. L’interface distingue les technologies détectées localement des fichiers sélectionnés pour un envoi après aperçu. Elle indique l’absence d’outils de lecture lorsque `tools` est faux ou le contexte est `SERVER_VERIFIED`. Avant une nouvelle question sans fichier et sans outil de lecture, elle propose d’ajouter des fichiers ou d’envoyer explicitement sans fichiers ; annuler conserve le brouillon et ne déclenche aucun appel au modèle. Une conversation existante conserve son historique ; cet avertissement ne présume pas que son contexte antérieur est vide.

### DEVAI-194 — Usage, quotas et coûts par fournisseur

Les métriques réconciliées enregistrent le fournisseur, l’alias, l’identifiant exact du modèle, les tokens d’entrée/sortie/total, le statut de mesure et, si disponible, le tarif, la devise et la date de référence. Les plafonds par projet, utilisateur et fournisseur ainsi que les réservations atomiques s’appliquent à tous les appels sans distinction de transport.

Une réponse sans mesure fiable reste `ESTIMATED` ou `PENDING_RECONCILIATION` et suit la règle existante ; elle n’est jamais comptée à zéro par défaut. Les messages de limite, d’authentification, d’indisponibilité et de délai d’attente nomment le fournisseur ou l’alias lorsque c’est utile, sans exposer le corps privé de sa réponse.

### DEVAI-195 — Qualification de confidentialité

Avant l’activation de chaque fournisseur, l’organisation documente les catégories de données transmises, la localisation, la conservation, les paramètres d’utilisation des données et les fonctions appelées. L’activation en production exige l’approbation de cette fiche par l’organisation ; une compatibilité technique ne constitue pas une approbation de confidentialité.

La politique de contexte, l’aperçu avant envoi, le filtrage des secrets, la minimisation des sources et les droits de projet continuent de s’appliquer. Les messages, réponses et traces ne sont jamais partagés entre projets ou fournisseurs. Toute panne est affichée sans envoyer les sources à un fournisseur de secours.

Pour OpenAI, l’organisation fournit une clé de l’API Platform, si l’API est disponible et autorisée. Une session ou un abonnement ChatGPT de l’utilisateur n’est pas traité comme une clé API. [Facturation API et ChatGPT](https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform)

## 6. Migration, tests et critères d’acceptation

### DEVAI-196 — Compatibilité et migration contrôlée

Une mise à jour conserve le fonctionnement des politiques existantes contenant uniquement `MISTRAL` et des déploiements actuels. La politique initiale reste Mistral seulement. Ajouter OpenAI nécessite une configuration administrée, un credential de service distinct, un modèle approuvé dans la politique signée et un build/livraison qui contient la version Vibe qualifiée. L’absence d’un de ces éléments produit un refus explicite, sans fallback.

Les recettes obligatoires sont :

1. Une configuration Mistral existante produit le même contrat et conserve les contrôles de quota, d’outil, de session et d’audit.
2. Un alias OpenAI signé et configuré passe par le backend Vibe générique avec le modèle, l’API style, l’URL et la variable de credential attendus. Le transport HTTP est simulé ; aucun test automatisé n’appelle OpenAI réellement.
3. Un provider non inscrit, modèle non signé, alias révoqué, mauvais style, endpoint non approuvé, credential absent, requête directe avec modèle libre ou signature invalide est refusé avant l’appel fournisseur.
4. Le test de contrat Vibe couvre réponse texte, appel d’outil et résultat d’outil, streaming, fin de tour, usage mesuré, limite de sortie, timeout, annulation, réponses malformées et erreurs d’authentification, quota et modèle inconnu.
5. Les tests prouvent qu’aucune clé ou URL privée ne se retrouve dans les logs, le DOM, les arguments, les exemples, les artefacts publics ou l’environnement transmis aux processus du workspace.
6. Les tests Windows et Linux couvrent le VSIX réellement assemblé, la version Vibe réellement incluse et ses licences. Une incompatibilité de l’adaptateur générique bloque la qualification ; elle ne déclenche pas un faux succès ni un autre modèle.
7. Un smoke test contre un compte fournisseur réel est facultatif et n’est exécuté qu’avec un credential de test fourni et une autorisation explicite. S’il n’est pas réalisé, sa compatibilité réelle est rapportée **NON VÉRIFIABLE**.

## 7. Résultat d’implémentation attendu et limites qualifiées

L’implémentation cible `OPENAI` via le backend `generic` et l’API style `openai` qualifiés sur le runtime Vibe 3.0.3 empaqueté. L’API style Responses et le streaming fournisseur restent désactivés tant que leur contrat n’est pas couvert par le runtime réel. Les modèles OpenAI peuvent utiliser génération, outils et usage mesuré ; images ne sont pas activées. Mistral reste le seul provider du profil par défaut. Le registre optionnel, la politique signée, les quotas provider et le bridge isolé sont décrits dans DEVAI-189 à DEVAI-196. DEVAI-197 précise que Java 21 et `ods.javaPath` s’appliquent à ODS uniquement ; un projet configuré en Java 25 doit utiliser son Maven, JDK, wrapper et `JAVA_HOME` installés.

La recette doit exécuter les tests Maven, le contrat HTTP simulé contre le runtime Vibe réellement empaqueté, les tests Python et le build de livraison avant que l’état du code soit déclaré validé. Aucun appel à un compte OpenAI réel n’est requis ; sans autorisation et credential de test séparés, ce smoke test reste **NON VÉRIFIABLE**.
### DEVAI-198 — Smoke OpenAI réel sur le banc Docker

Le corps HTTP produit par le backend générique Vibe 3.0.3 est adapté par le bridge ODS : retrait de `temperature`, remplacement de `max_tokens` par `max_completion_tokens`, et `reasoning_effort=none` pour les familles GPT-6 Luna/Sol qualifiées. Le client TLS refuse les redirections. Les fichiers de la distribution Vibe restent inchangés ; cette adaptation et les erreurs HTTP sont vérifiées sur le runtime empaqueté.

Le banc Docker PEUT activer explicitement un test réel OpenAI au moyen de `provision.py --openai-smoke` et `native-smoke.cjs --real-openai`. L’option exige une clé API Platform renouvelée fournie par `OPENAI_API_KEY`, la place seulement dans le profil privé ignoré par Git et signe une politique temporaire qui autorise OpenAI pour le seul projet de démonstration autorisé. Le test passe par le client natif ODS, le gateway local, GitLab Docker et cette politique signée ; il envoie une seule instruction synthétique sans fichier source et sans outil, puis valide fournisseur, modèle et usage mesuré. Le modèle qualifié pour ce smoke est `gpt-6-luna` via Chat Completions. Le bridge omet les paramètres de température pour cet adaptateur OpenAI. Aucun test automatisé par défaut n’appelle un fournisseur réel ; le résultat ne doit contenir ni clé ni réponse complète. L’opérateur restaure ensuite le profil/politique Mistral avec la procédure normale du banc.

**Recette :** sans option explicite, compteur d’appels réels égal à zéro ; avec option et credential, une requête OpenAI au plus, réponse attribuée à `OPENAI`/`gpt-6-luna` et compteurs fournisseur positifs ; sans credential, arrêt avant provisionnement et appel fournisseur.
