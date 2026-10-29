# Runner Sonar et Xray

Le connecteur lance les API officielles de pipelines GitLab, les suit et demande leur annulation. Il ne soumet pas le `pom.xml` d'un projet à l'exécution dans la gateway. Le résultat distingue le statut du pipeline et le rapport du fournisseur.

Installer le contenu de `examples/runner/` dans un projet GitLab de scanners distinct et protégé. Configurer `ODS_RUNNER_PROJECT_ID`, `ODS_RUNNER_REF`, `ODS_RUNNER_TOKEN` dans la gateway. Ajouter le projet runner à l'allowlist des job tokens des projets analysés, avec lecture des archives uniquement. La gateway vérifie d'abord les droits du développeur et l'existence du commit cible.

## Préparation administrative nécessaire

- Fournir `ODS_RUNNER_IMAGE` avec une **image interne qualifiée et fixée par digest** contenant Python, Java 21/Maven, Node/npm, SDK .NET, SonarScanner CLI/.NET et JFrog CLI compatible avec `--static-sca`.
- Utiliser un exécuteur éphémère Docker/Kubernetes non privilégié, sans socket Docker ni montage du poste/hôte, avec limites CPU/mémoire/durée et egress limité aux services et registres autorisés.
- Utiliser des variables GitLab protégées et limitées par environnement : `SONAR_TOKEN`, `SONAR_HOST_URL`, `JFROG_URL`, `JFROG_ACCESS_TOKEN` pour `ods-scanner` uniquement. Aucun de ces secrets ne doit être défini globalement pour `ods-build`.
- Définir `ODS_PROJECT_MAPPING`, par exemple `{"1042":{"sonarKey":"example-service"}}`, comme configuration administrative protégée. Tout projet absent est refusé ; le développeur ne choisit pas librement la clé Sonar.
- Limiter le compte qui crée les pipelines aux seuls projets autorisés. Ne pas permettre de modifier le script du scanner via les fichiers du projet analysé.

Les commandes de build exécutent du code du dépôt. La séparation des jobs et les environnements nettoyés ne remplacent pas l'isolation de l'exécuteur. Le scanner .NET implique une compilation instrumentée avec accès au scanner : utiliser des identifiants éphémères limités au projet et un runner dédié qualifié.

## Résultats et périmètre

L'archive source est vérifiée contre le commit demandé. L'extraction refuse traversées, liens, collisions et dépassements. La phase build doit réussir avant la phase scan ; les codes de retour sont conservés. Le scan Sonar CLI attend le Quality Gate. Le traitement .NET et les rapports historiques doivent aussi être relus via l'API Sonar. Les jobs et artefacts GitLab donnent le diagnostic de l'exécution réelle.

Le mode Xray livré utilise **Static SCA**, sans exécuter le gestionnaire de paquets pendant l'audit. Il dépend des manifestes/locks reconnus par la version JFrog qualifiée ; il ne prétend pas résoudre toutes les dépendances transitives absentes des locks. Le rapport `work/xray.json` et `scan-result.json` restent des artefacts CI protégés et expirent après un jour. La lecture v2 d'un build Xray publié reste disponible séparément dans l'extension.

Références officielles : [Pipelines GitLab](https://docs.gitlab.com/api/pipelines/), [job tokens](https://docs.gitlab.com/ci/jobs/ci_job_token/), [SonarScanner CLI](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/scanners/sonarscanner), [JFrog audit](https://docs.jfrog.com/security/docs/scan-your-source-code), [authentification CLI par stdin](https://docs.jfrog.com/integrations/docs/authenticating-via-the-cli).

Ce runner nécessite une qualification réelle dans l'infrastructure cible. Aucune image interne, instance Sonar/Xray ou exécution distante n'est inventée par les exemples.
