# Construire un VSIX pour une organisation

Copier `config/organisations/demo` vers un dossier administrateur externe au dépôt. Remplacer les placeholders dans `gateway.env.example` (ou renommer le fichier et adapter `envFiles`) et renseigner les mappings Sonar. Le profil JSON accepte `schemaVersion`, `id`, `extension`, `envFiles`, `environment` et `files`.

```json
{
  "schemaVersion": 1,
  "id": "organisation-demo",
  "extension": {
    "name": "organisation-development-standard",
    "publisher": "ods-internal",
    "displayName": "ORGANISATION DEVELOPMENT STANDARD"
  },
  "envFiles": ["gateway.env"],
  "environment": {"ODS_LLM_TRANSPORT": "VIBE_CLI"},
  "files": [{"source": "application-organisation.properties", "target": "application-organisation.properties"}]
}
```

`envFiles` est chargé dans l’ordre, puis `environment` prend priorité. Les chemins de fichiers sont relatifs au profil et ne peuvent pas en sortir. L’ancien champ `gatewayUrl` reste accepté pour lire un ancien profil, mais il ne redirige plus le gateway embarqué. Le port est choisi automatiquement.

Depuis un terminal `cmd.exe`, à la racine des sources :

```bat
set "BUILD_MISTRAL_KEY=<VALEUR_FOURNIE_PAR_ORGANISATION>"
script.bat "<DOSSIER_ADMIN>\organisation.json"
```

Dans le fichier env, `ODS_MISTRAL_API_KEY=${BUILD_MISTRAL_KEY}` demande explicitement la résolution au packaging. Une valeur absente arrête le build. Les URL et paramètres non secrets peuvent être des valeurs littérales. Ne pas transmettre un secret comme argument de commande. Effacer les variables de la session de build après usage selon la procédure de l’organisation.

`script.bat` exécute validation, compilation, tests et création de `suiteInstall`. `generate-vsix.bat "<PROFIL>"` produit seulement le VSIX après la même validation. `build.bat` exécute la validation sans packaging. Tous les scripts batch du produit appellent Python, sans appel PowerShell. Sans profil, le packaging utilise le profil de démonstration, qui n’autorise aucune connexion à un service réel.

Le runtime contient la gateway, la configuration compilée, les adaptateurs et, en mode Vibe, la distribution tierce figée. L’identité et le titre de l’extension viennent du profil ; aucun fichier source n’est réécrit avec les secrets. Installer une seule variante ODS par profil VS Code, car les commandes restent `ods.*`.

Les valeurs et deux modes d’authentification Xray sont détaillés dans [CONFIGURATION](CONFIGURATION.md). Les profils privés, VSIX personnalisés et archives correspondantes ne doivent pas entrer dans Git ou les artefacts CI génériques. Les anciens instantanés explicitement demandés sur `dev/toreprise` et `script/vsix` restent historiques ; la branche 0.3.0 revient aux artefacts hors Git.

Le packaging privé autorise explicitement les identifiants dans `runtime/deployment.json`. Le contrôle générique de secrets de `vsce`, susceptible de les afficher dans ses erreurs, est remplacé pour ce paquet par le contrôle de la composition prévue ; tout fichier `.env` ou `.env.*` dans le staging est refusé. Cette exception ne concerne jamais les sources Git ni les publications publiques.

Dans `suiteInstall/config/gateway.env`, les valeurs sensibles sont remplacées par des placeholders : les credentials compilés restent dans le VSIX privé. Les fichiers compagnons déclarés sous `files` doivent contenir uniquement les mappings et réglages non secrets ; placer les identifiants dans les variables `ODS_*` prévues.
