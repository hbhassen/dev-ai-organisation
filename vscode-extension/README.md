# Organisation Development Standard

Extension interne VS Code Desktop avec cinq vues : Project Initializer, IA Code Generator, Quality, Application Security et GitFlow.

Prérequis : VS Code Desktop 1.95.0 ou supérieur dans la série 1.x (dont 1.95.2), Java 21 et Git, puis SDK de la stack générée. Installer le VSIX, ouvrir un workspace approuvé et lancer **ODS: Ouvrir les outils**. Les starters standards fonctionnent sans IA. Le chat et l'agent nécessitent la gateway configurée, SSO, accès GitLab et une politique signée qui autorise un modèle Mistral.

Le réglage machine `ods.gatewayUrl` vaut `http://127.0.0.1:8080` pour le développement local ; utiliser HTTPS pour le serveur organisationnel. `ods.javaPath` permet de choisir Java 21 sans changer l'environnement global. Les jetons utilisateur sont conservés par SecretStorage ; aucune clé Mistral ou compte technique n'est embarqué.

Le mode Agent lit le projet, propose un diff, demande les autorisations nécessaires puis peut lancer les commandes et analyser leurs résultats. Les outils ne donnent aucun droit supplémentaire côté serveur. Les scans distants nécessitent un runner isolé configuré ; l'absence de ce service est signalée explicitement.
