"""Compile, teste et distribue uniquement mecanique, sans aucun build ou test de l'extension. MECA-007."""
from importlib.metadata import version as installed_version
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent


def run(args, cwd=ROOT, env=None):
    """Arrête la livraison dès qu'une validation propre au mini-projet échoue. MECA-007."""
    subprocess.run([str(a) for a in args], cwd=cwd, env=env, check=True)


def main():
    """Exécute les tests isolés, construit une livraison et vérifie son lancement hors des sources. MECA-007."""
    if sys.version_info < (3, 11):
        raise SystemExit("Python 3.11 ou supérieur requis.")
    if not shutil.which("git"):
        raise SystemExit("Git requis pour les tests et la publication.")
    expected = (ROOT / "requirements.txt").read_text().strip().split("==")[1]
    try:
        if installed_version("cryptography") != expected:
            raise ValueError()
    except Exception:
        raise SystemExit("Installer les dépendances du mini-projet : python -m pip install -r requirements.txt") from None
    version = (ROOT / "VERSION").read_text().strip()
    environment = dict(os.environ, PYTHONPATH=str(ROOT / "src"), PYTHONIOENCODING="utf-8")
    run([sys.executable, "-m", "compileall", "-q", ROOT / "src", ROOT / "run.py", ROOT / "build.py"])
    run([sys.executable, "-m", "unittest", "discover", "-s", ROOT / "tests", "-v"], env=environment)
    # MECA-007: generated directories are ignored; preserve previous deliveries rather than deleting them.
    output = ROOT / "suiteInstall"
    if output.exists():
        previous = ROOT / ".build" / ("previous-" + uuid.uuid4().hex)
        previous.parent.mkdir(exist_ok=True)
        output.rename(previous)
    output.mkdir()
    for directory in ("src", "tests"):
        shutil.copytree(ROOT / directory, output / directory, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
    for filename in ("run.py", "run.bat", "build.py", "build.bat", "requirements.txt", "VERSION", "README.md", "CHANGELOG.md", "SPECIFICATION.md"):
        shutil.copy2(ROOT / filename, output / filename)
    clean_environment = dict(os.environ, PYTHONPATH="", PYTHONIOENCODING="utf-8")
    run([sys.executable, output / "run.py", "--help"], cwd=output, env=clean_environment)
    try:
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        dirty = bool(subprocess.check_output(["git", "status", "--porcelain", "--", "."], cwd=ROOT, text=True).strip())
    except subprocess.CalledProcessError:
        commit, dirty = "unversioned", True
    (output / "BUILD.json").write_text(json.dumps({"version": version, "sourceCommit": commit, "sourceDirty": dirty, "tests": "SUCCESS", "scope": "mecanique only"}, indent=2) + "\n", encoding="utf-8")
    archive = ROOT / f"suiteInstall-{version}.zip"
    if archive.exists():
        backups = ROOT / ".build"
        backups.mkdir(exist_ok=True)
        shutil.copy2(archive, backups / ("previous-" + uuid.uuid4().hex + ".zip"))
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(output.rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts:
                bundle.write(path, path.relative_to(ROOT))
    with zipfile.ZipFile(archive) as bundle:
        if bundle.testzip() is not None:
            raise RuntimeError("Archive invalide.")
    print(f"SUCCESS mecanique {version}: tests, compilation Python, livraison autonome et archive vérifiés.")


if __name__ == "__main__":
    main()
