# ODS 0.5.0 - livraison privee de test local

Installer bin/ods-docker-manual-0.5.0.vsix dans le profil Test. Les services attendus sont sur localhost. Voir docs/MANUAL-TESTS.md. Le VSIX contient les credentials du banc : ne pas publier cette archive. Le poste cible doit disposer de Java 21, Python 3.12 et des outils Maven/Git, et approuver config/ca.crt dans le JDK utilise. Aucun service Docker ni secret de signature prive ne figure dans cette livraison.
