# Exigences du mini-projet Mécanique

Ces références sont propres à l'outil ; le référentiel et la version de l'extension ne sont pas modifiés.

- **MECA-001** : checkout Git temporaire de la branche explicite, limité à `ai-project-policy.json` et `ai-project-policy.sig`. Aucun fichier tiers matérialisé ou modifié.
- **MECA-002** : validation du contrat de politique ODS 0.6.0, types, clés uniques, références, quotas, contexte et mapping Sonar. Aucune suppression silencieuse des champs inconnus.
- **MECA-003** : vérification de la signature existante, clés Ed25519 Base64 DER, signature détachée des octets finaux, incrément unique et intervalle UTC de 24 heures. Une politique expirée peut être renouvelée sans contourner la signature.
- **MECA-004** : questions explicatives par propriété, valeurs possibles, ajout de plusieurs projets et modèles, édition des règles, aperçu et confirmation avant publication.
- **MECA-005** : un commit modifiant exactement les deux fichiers, vérification de la tête distante, push sans force, détection des conflits, confirmation distante et brouillon récupérable en cas d'échec.
- **MECA-006** : aucun token en URL ou argument, usage de l'authentification Git existante, clés jamais copiées dans le checkout ou la livraison, erreurs Git filtrées, nettoyage limité aux répertoires temporaires possédés.
- **MECA-007** : compilation, tests et package propres au dossier `mecanique`, tests Git sur dépôts locaux, aucun appel métier ni build/test de l'extension. Livraison versionnée, documentation et tests inclus.
