"""Entrée du mini-projet indépendante du moteur et du build VSIX. MECA-001."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))

if __name__ == "__main__":
    try:
        from meca.cli import main
    except ImportError:
        raise SystemExit("Installer les dépendances : python -m pip install -r requirements.txt") from None
    raise SystemExit(main())
