# Installation ODS 0.2.1

## Prérequis de la machine cible

Windows, VS Code Desktop 1.95.0 ou supérieur dans la série 1.x (dont 1.95.2) et Java 21 sur PATH. Les outils des projets ne sont pas embarqués : Git, Node/npm, Python, Maven Wrapper et SDK .NET selon la stack. Angular CLI 21.2.5 accepte notamment Node 24. Un simple runtime .NET ne suffit pas à créer un starter.

Le lanceur `scripts/start-gateway.bat` nécessite **Python 3.11+ sur PATH** en plus de Java 21. Les scripts batch du projet, y compris le build et la génération VSIX, ne font aucun appel PowerShell. L'installation du VSIX utilise uniquement la CLI `code`.

## Depuis suiteInstall

Le manifeste du VSIX déclare `engines.vscode: "^1.95.0"` : VS Code accepte son installation à partir de 1.95.0 et avant 2.0.0. Les versions antérieures restent exclues ; la compatibilité avec toutes les versions passées ou futures n'est pas garantie. Le code est compilé avec `@types/vscode` verrouillé à `1.95.0`, afin de détecter l'utilisation d'API VS Code plus récentes.

Si VS Code 1.95.2 refuse l'ancien VSIX 0.2.0, installer le **VSIX 0.2.1 reconstruit** ci-dessous : modifier le manifeste du projet ne change pas une archive déjà créée. Depuis les sources, `generate-vsix.bat` recrée le VSIX après compilation et tests. Voir la [règle officielle de compatibilité](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#visual-studio-code-compatibility).

1. Extraire `suiteInstall-0.2.1.zip` et conserver son dossier `suiteInstall/`.
2. Exécuter `scripts\install-extension.bat` ou « Extensions: Install from VSIX » avec le fichier de `bin/`.
3. Exécuter `scripts\start-gateway.bat`. Sans configuration privée, seul le démarrage local et les endpoints de santé sont disponibles.
4. Pour les services réels, copier `config/gateway.env.example` en `config/gateway.env`, remplacer les exemples puis relancer la gateway. Protéger ce fichier ; ne jamais le commiter.
5. Dans VS Code, renseigner `ods.gatewayUrl` puis ouvrir **ODS: Ouvrir les outils**. La valeur locale est `http://127.0.0.1:8080` ; utiliser HTTPS pour un serveur distant.
6. Ouvrir et approuver le dossier, connecter le SSO puis le compte GitLab utilisateur. Le serveur vérifie la correspondance OIDC/GitLab avant toute opération distante.

Pour choisir un autre fichier de configuration, utiliser `scripts\start-gateway.bat --config "<CONFIGURATION>/gateway.env"` ; l'ancien argument `-Config` reste accepté. Les valeurs sont lues littéralement, sans exécution de shell ; une référence `${VARIABLE}` exige une variable d'environnement non vide.

Les JAR et le VSIX sont autonomes par rapport au checkout source. Le JDK, VS Code, les services et les outils de développement restent des prérequis externes. Contrôler les empreintes de `SHA256SUMS` avant une installation organisationnelle.

## Usage

- **Starter** : choisir la stack, le nom et le dossier parent. Une destination existante est refusée. Le résultat indique séparément génération et validation.
- **IA / IA Code Generator** : Question lit le contexte ; Modification propose des diffs ; Agent peut exécuter les commandes approuvées, lire leurs résultats et corriger. Les fichiers sales de VS Code et les bases modifiées bloquent l'application. Les permissions de commande concernent les arguments exacts. Le trombone sélectionne jusqu’à 10 fichiers (100 Ko au total), présentés avant envoi. Entrée envoie, Maj+Entrée ajoute une ligne. Choisir les fichiers à appliquer dans le sélecteur natif du diff. La consommation mensuelle provient de la gateway.
- **Quality / Security** : lancer un pipeline dans le projet runner dédié ou consulter les rapports réels. Les lectures de rapports ne prétendent pas avoir exécuté un scanner. Ces fonctions n'exigent pas un droit IA. Le bouton Corriger sélectionne les issues et exige le droit de remédiation distinct ; une correction n'est considérée résolue qu'après nouveau scan.
- **GitFlow** : examiner l'état/diff, créer une branche, sélectionner les fichiers à commiter, pousser et ouvrir une MR avec vos droits utilisateur.

## Arrêt et récupération

Le bouton Arrêter demande l'arrêt du job et termine les processus locaux possédés. Un appel fournisseur déjà facturé reste comptabilisé. Les anciennes versions de fichiers sont conservées sous `.ods-local/backups/` dans le projet. La restauration contrôlée refuse d'écraser une modification utilisateur postérieure.

Ne pas assimiler une fermeture de fenêtre à un rollback Git. Les connexions réelles Mistral, SSO, GitLab, Sonar, Xray et PostgreSQL doivent être qualifiées dans l'environnement cible avant une mise en production.

La matrice [CONFORMITE.md](CONFORMITE.md) distingue fonctionnalités présentes et écarts restants. Cette version de développement ne constitue pas la recette complète du référentiel.

## Variante organisationnelle

Voir [PROFILS-ORGANISATION.md](PROFILS-ORGANISATION.md) pour reconstruire avec le nom, les URL et les fichiers de configuration d’une organisation. Le profil livré contient des paramètres d’exemple et ne permet pas une connexion réelle avant remplacement.
