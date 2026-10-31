# Banc Docker pour les tests manuels ODS 0.3.0

Ce dossier ajoute un environnement de test sans modifier les sources existantes de l'extension, du moteur Java ou du gateway. Le build utilise le profil d'organisation déjà pris en charge par le projet. Aucun appel de génération Mistral n'est effectué par les scripts de validation de ce dossier.

| Service | Adresse locale | Nature |
|---|---|---|
| GitLab CE | https://localhost:18443 | Instance réelle, dépôts privés |
| SonarQube Community | https://localhost:19443 | Serveur et scanner réels, base H2 d'évaluation |
| mockXray | https://localhost:18083/health | Simulation Graph Scan, résultats fictifs |
| Gateway ODS | Port loopback dynamique | Processus démarré et arrêté par l'extension |

Les ports Docker sont liés uniquement à `127.0.0.1`. Les volumes nommés conservent les données. `docker compose stop` arrête les services ; ne pas utiliser `down -v` pour une simple pause.

## Comptes et données privées

`serviceExtebtion` lit les dépôts GitLab pour le gateway. `user1` dispose d'un accès en écriture aux deux projets de démonstration et d'un accès en lecture à la gouvernance. Son mot de passe de test a été fourni séparément ; il n'est pas versionné. L'authentification Git utilise un token utilisateur dans Git Credential Manager. Les opérations du développeur n'utilisent pas le token du service.

Les secrets, clés privées TLS/signature, tokens et profils compilés sont sous `.local/`, ignoré par Git. Le VSIX contient les secrets de connexion conformément au mécanisme existant : conserver le VSIX et sa livraison localement. Ne pas les publier sur GitHub ni joindre le dossier `.local/` à un rapport. Aucun mot de passe de compte Mistral n'est nécessaire au fonctionnement de l'extension.

Pour la connexion navigateur SonarQube, le compte est `admin` et son mot de passe local généré se trouve dans `.local/sonar.json`. Le mot de passe généré de `serviceExtebtion` se trouve dans `.local/bootstrap/accounts.json`. Ces fichiers sont exclusivement privés au poste de test.

Un JDK 21 isolé sous `.local/jdk/` contient l'autorité TLS du banc. Le JDK installé sur le poste reste inchangé. Les clones Git utilisent cette autorité explicitement, avec vérification TLS active.

Les tokens GitLab de ce banc expirent après 90 jours. La politique IA, distincte des tokens, doit être renouvelée chaque jour de test. L'autorité TLS locale est aussi importée dans le magasin utilisateur Windows pour la navigation ; elle peut être retirée après abandon du banc avec `certutil -user -delstore Root "ODS Manual Test Local CA"`.

Sur ce poste, le lanceur utilise la copie **VS Code 1.95.2** déjà disponible, car l'installation habituelle présente une mise à jour en attente et n'ouvrait pas la nouvelle fenêtre. Son chemin est enregistré uniquement dans `.local/vscode-executable.txt`. Les données et extensions du profil `Test` sont indépendantes des profils habituels.

## Parcours manuel

1. Ouvrir le profil VS Code **Test**, isolé avec ses propres données et extensions, sur `.local/workspaces/demo-allowed`.
2. Ouvrir **ODS Docker Manual Test** dans la barre d'activité. Le lanceur dédié utilise `--disable-workspace-trust` pour cette session de test sur les seuls projets de démonstration créés ici. Les profils habituels ne sont pas modifiés. Pour une ouverture manuelle sans ce lanceur, approuver le dossier si VS Code le demande. Le contrôle Git précède toujours le démarrage du gateway.
3. Vérifier le projet détecté et les droits IA. Le dépôt `ods-manual/governance` contient `ai-project-policy.json` et sa signature Ed25519.
4. Dans **Security**, générer un SBOM Java puis demander le rapport Xray. Attendre le passage par l'état en cours. Les résultats `XRAY-MOCK-0001` sont synthétiques ; ils servent à tester l'affichage, pas à évaluer les dépendances.
5. Dans **Quality**, lancer l'analyse Java. L'extension lance Maven/SonarScanner sur les fichiers enregistrés, puis lit le résultat du serveur SonarQube.
6. Modifier et enregistrer un fichier du projet de démonstration, sans commit, puis relancer une analyse. Un ancien SBOM doit être refusé si les sources ont changé.
7. Ouvrir `.local/workspaces/demo-denied` dans le même profil. Git est accessible, mais les actions IA sont refusées par la politique signée.
8. Quand vous le décidez, effectuer un petit appel IA sur le projet autorisé. Le modèle configuré est **Mistral Medium 3.5**, identifiant `mistral-medium-3-5`. Ce parcours consomme votre quota ; il reste volontairement hors des tests automatiques.

La [fiche officielle Mistral](https://docs.mistral.ai/models/mistral-medium-3-5-26-04) indique une dépréciation au 31 octobre 2026. Le modèle demandé est conservé ; la validité de la clé, le quota et la disponibilité effective du modèle ne sont pas vérifiés par un appel payant.

## Reprendre une séance

Depuis la racine du projet :

```powershell
./TestDOKERMan/start.ps1 -Open
```

Attendre que GitLab et Sonar soient prêts après un redémarrage, puis relancer si nécessaire. Le script renouvelle la politique signée : le code existant impose une validité maximale de 24 heures. Le gateway recharge la politique au plus tard après son cache de 60 secondes. Le renouvellement ne nécessite pas de reconstruire le VSIX.

Pour reconstruire et réinstaller après un changement de connexion ou de clé :

```powershell
./TestDOKERMan/start.ps1 -Build -Install -Open
```

Le build exécute les contrôles, tests et packaging existants. Le profil privé se trouve dans `.local/profile/organisation.json`. La version applicative reste `0.3.0`.

## Reproduction initiale

Prérequis : Docker Desktop Linux, Java 21, Node 24/npm, Python 3.12, OpenSSL et VS Code CLI. `prepare.py` génère les secrets locaux et certificats, copie le JDK et prépare le bootstrap GitLab. Fournir le mot de passe local par `ODS_TEST_USER_PASSWORD` si nécessaire. Après démarrage Docker et disponibilité de GitLab :

```powershell
python TestDOKERMan/prepare.py
certutil -user -addstore Root TestDOKERMan/.local/tls/ca.crt
docker compose -f TestDOKERMan/compose.yaml up -d --build
docker compose -f TestDOKERMan/compose.yaml exec -T gitlab gitlab-rails runner /bootstrap/seed_accounts.rb
docker compose -f TestDOKERMan/compose.yaml cp gitlab:/tmp/ods-accounts.json TestDOKERMan/.local/accounts.json
```

N'exécuter le bootstrap de comptes qu'une fois. Fournir la clé Mistral dans `.local/mistral-api-key.txt`, puis exécuter `python TestDOKERMan/provision.py` après le build Java. Ce script crée les projets, signe la politique, configure Sonar, prépare le profil et clone les deux dépôts de démonstration. Les secrets ne sont jamais affichés.

## Validation et limites

`python -m unittest discover -s TestDOKERMan/mockXray -p test_app.py -v` teste l'authentification, les erreurs et le cycle asynchrone du mock par HTTP. `node TestDOKERMan/native-smoke.cjs <EXTENSION_INSTALLEE>` utilise les classes natives compilées de l'extension, le vrai moteur Java, le vrai gateway et les services Docker. Seuls le stockage et l'accès à la configuration VS Code sont substitués dans ce test ; l'observation de la vraie interface reste distincte.

Le mock suit les [structures Graph Scan du client officiel JFrog](https://github.com/jfrog/jfrog-client-go/blob/master/xray/services/scan.go) : `POST /xray/api/v1/scan/graph`, `scan_id`, polling `202`, puis `vulnerabilities`, `violations`, `licenses`, composants, versions corrigées et chemins d'impact. Il ne simule ni un moteur de détection, ni toute la plateforme JFrog. Ses scans sont en mémoire et disparaissent au redémarrage.

Le script `native-smoke.cjs` écrit son résultat effectif dans `.local/native-smoke-result.json`. Consulter `VALIDATION.md` pour le bilan réellement exécuté. Ce banc local n'est pas une release de production.

**Défaut existant reproduit :** `GET /api/v1/workspace/fingerprint` renvoie les propriétés internes d'un nœud Jackson au lieu du champ `fingerprint`. Le contrôle de fraîcheur du front avant une correction IA ne peut donc pas aboutir. Les sources applicatives sont conservées telles quelles ; le test d'intégration enregistre cet échec et sort avec le code 1. Les scans indépendants restent vérifiés séparément.
