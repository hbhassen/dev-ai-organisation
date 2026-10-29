# Instantané de reprise — ODS 0.2.0

Version : 0.2.0. Tag : `v0.2.0`. Source : `db388aff104ba6ef1a87000267a0f9e1e09fa93a`.

Cette branche conserve, par demande explicite, le dossier `suiteInstall` extrait sans recompilation de la [préversion GitHub](https://github.com/hbhassen/dev-ai-organisation/releases/tag/v0.2.0). Son commit d’instantané est distinct du commit source désigné par `BUILD.json`.

Archive d’origine : `suiteInstall-0.2.0.zip` — SHA-256 `f09951f904fe555029d18f436e311d2a8d6e86e57265967c54746e75a2454f57`.

Le package contient les JAR, le VSIX, les scripts de démarrage, les configurations et la documentation. Les configurations actuelles sont des exemples à renseigner ; aucun secret réel d’organisation n’a été fourni. Les clés privées des mocks sont éphémères et ne sont pas des accès organisationnels.

Build et tests : SUCCESS. 46 tests Java, 13 tests Node, 6 tests navigateur et 6 tests Python ; CI Windows/Linux et PostgreSQL, activation VSIX réelle au même commit et démarrage des JAR livrés. Voir [validation](suiteInstall/docs/VALIDATION.md) et [profils](suiteInstall/docs/PROFILS-ORGANISATION.md). Les services réels restent à qualifier et des exigences demeurent partielles dans la matrice.

Les attributs Git préservent les octets de cet instantané. Ne pas fusionner les artefacts vers main/develop ; poursuivre le développement sur les branches source et utiliser `script.bat config\organisations\demo\organisation.json` depuis le checkout.
