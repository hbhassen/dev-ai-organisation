"""Checkout Git restreint, publication atomique des deux fichiers et refus des courses. MECA-001, MECA-005."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
from urllib.parse import urlsplit

from .policy import FILES, MecaError


def repository_url(value):
    """Accepte HTTPS/SSH ou un dépôt local de recette, jamais un token dans une URL. MECA-006."""
    if not value or value.startswith("-") or any(c in value for c in "\r\n\x00"):
        raise MecaError("Adresse du dépôt invalide.")
    if Path(value).is_dir():
        return str(Path(value).resolve())
    if re.fullmatch(r"[A-Za-z0-9_.-]+@[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+", value):
        return value
    parsed = urlsplit(value)
    if parsed.scheme not in ("https", "ssh") or not parsed.hostname or parsed.password or parsed.query or parsed.fragment or (parsed.scheme == "https" and parsed.username):
        raise MecaError("Utiliser HTTPS sans credentials, SSH, ou un dépôt local ; authentification via Git existant.")
    return value


class Repository:
    """Possède un checkout temporaire distinct du projet utilisateur et ne pousse jamais avec force. MECA-001, MECA-005."""

    def __init__(self, remote, branch, name=None, email=None):
        """Retient la branche explicite et l'identité facultative sans conserver de credential. MECA-001, MECA-006."""
        self.remote = repository_url(remote)
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_./-]*", branch):
            raise MecaError("Nom de branche invalide.")
        self.branch, self.name, self.email = branch, name, email
        self.path = None
        self.temporary = None
        self.base = None

    def git(self, *args, input_data=None):
        """Exécute Git avec timeout, hooks désactivés et erreurs sans contenu sensible. MECA-005, MECA-006."""
        environment = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
        environment.update(GIT_TERMINAL_PROMPT="0", GCM_INTERACTIVE="never")
        command = ["git", "-c", "core.hooksPath=" + str(self.path / ".git" / "meca-empty-hooks"), "-c", "protocol.ext.allow=never", "-c", "core.fsmonitor=false", *args]
        try:
            result = subprocess.run(command, cwd=self.path, env=environment, input=input_data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
        except (OSError, subprocess.TimeoutExpired):
            raise MecaError("Git absent, indisponible ou délai dépassé. Vérifier Git, le réseau et l'authentification du poste.") from None
        if result.returncode:
            raise MecaError("Git " + args[0] + " refusé. Vérifier la branche, les droits, l'identité Git et les éventuelles modifications concurrentes.")
        return result.stdout

    def __enter__(self):
        """Récupère une branche et matérialise uniquement JSON et SIG, sans checkout complet. MECA-001."""
        self.temporary = tempfile.TemporaryDirectory(prefix="meca-checkout-")
        self.path = Path(self.temporary.name)
        try:
            self.git("init", "--quiet")
            self.git("check-ref-format", "--branch", self.branch)
            self.git("config", "core.autocrlf", "false")
            self.git("config", "core.safecrlf", "false")
            self.git("config", "commit.gpgsign", "false")
            if self.name:
                self.git("config", "user.name", self.name)
            if self.email:
                self.git("config", "user.email", self.email)
            # MECA-003: attributes are local to this checkout, never a third published file.
            (self.path / ".git" / "info" / "attributes").write_text("\n".join(f"/{name} -text -filter -ident -working-tree-encoding" for name in FILES) + "\n", encoding="utf-8")
            self.git("remote", "add", "origin", self.remote)
            self.git("fetch", "--quiet", "--depth=1", "--filter=blob:none", "origin", "refs/heads/" + self.branch)
            self.base = self.git("rev-parse", "FETCH_HEAD").decode().strip()
            for name in FILES:
                entry = self.git("ls-tree", self.base, "--", name).decode().strip()
                if not entry or entry.split()[0] not in ("100644", "100755"):
                    raise MecaError("Le dépôt doit contenir deux fichiers réguliers : " + ", ".join(FILES))
            self.git("sparse-checkout", "init", "--no-cone")
            self.git("sparse-checkout", "set", "--no-cone", "--stdin", input_data=("\n".join("/" + name for name in FILES) + "\n").encode())
            self.git("checkout", "--quiet", "-b", "meca-work", self.base)
            for name in FILES:
                if (self.path / name).is_symlink() or (self.path / name).read_bytes() != self.git("show", self.base + ":" + name):
                    raise MecaError("Checkout non fidèle aux octets Git de la politique.")
            return self
        except Exception:
            self.temporary.cleanup()
            raise

    def __exit__(self, *_):
        """Nettoie uniquement le répertoire temporaire créé par cet objet. MECA-006."""
        self.temporary.cleanup()

    def read(self):
        """Lit les deux seuls fichiers éditables du checkout restreint. MECA-001."""
        return tuple((self.path / name).read_bytes() for name in FILES)

    def remote_head(self):
        """Résout la tête distante sans changer le checkout ni intégrer une autre modification. MECA-005."""
        result = self.git("ls-remote", "--exit-code", "origin", "refs/heads/" + self.branch).decode().split()
        if len(result) != 2:
            raise MecaError("Branche distante introuvable ou ambiguë.")
        return result[0]

    def publish(self, document, signature, version):
        """Stage uniquement les deux fichiers, vérifie les octets, commit puis push normal et confirmation distante. MECA-003, MECA-005."""
        try:
            if self.remote_head() != self.base:
                raise MecaError("La branche a changé depuis la lecture : relancer l'assistant pour intégrer sa nouvelle politique.")
            for name, content in zip(FILES, (document, signature)):
                (self.path / name).write_bytes(content)
            self.git("add", "--", *FILES)
            staged = set(self.git("diff", "--cached", "--name-only", "-z").decode().strip("\0").split("\0"))
            if staged != set(FILES):
                raise MecaError("Le commit doit modifier exactement les deux fichiers de gouvernance.")
            for name, content in zip(FILES, (document, signature)):
                if self.git("show", ":" + name) != content:
                    raise MecaError("Git a transformé les octets signés ; publication annulée.")
            self.git("commit", "--quiet", "-m", f"chore: update signed AI policy v{version}")
            commit = self.git("rev-parse", "HEAD").decode().strip()
            self.git("push", "--porcelain", "origin", "HEAD:refs/heads/" + self.branch)
            if self.remote_head() != commit:
                raise MecaError("La tête distante ne correspond plus au commit ; publication non confirmée, vérifier GitLab.")
            return commit
        except (MecaError, KeyboardInterrupt, OSError) as error:
            recovery = Path(tempfile.mkdtemp(prefix="meca-recovery-"))
            for name, content in zip(FILES, (document, signature)):
                (recovery / name).write_bytes(content)
            reason = str(error) if isinstance(error, MecaError) else "Publication interrompue ou erreur locale ; vérifier la tête distante."
            raise MecaError(f"{reason}\nBrouillon signé conservé dans : {recovery}\nNe pas le pousser en force ; relancer depuis la nouvelle tête distante.") from None
