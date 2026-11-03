"""Recette avec vrais dépôts Git locaux : sparse checkout, push, conflits et fichiers tiers. MECA-007."""
from copy import deepcopy
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from meca.cli import generate_keys, main
from meca.policy import FILES, MecaError, decode, encode, load_keys, renew, sign, verify
from meca.repository import Repository, repository_url
from test_policy import fixture


class RepositoryTests(unittest.TestCase):
    """Teste la publication réelle sur un bare local, sans services Docker ni extension. MECA-001, MECA-005, MECA-007."""

    def git(self, *args, cwd=None, data=None):
        """Exécute Git sur la fixture isolée et conserve ses diagnostics pour les assertions de test. MECA-007."""
        return subprocess.run(["git", "-c", "commit.gpgsign=false", *args], cwd=cwd or self.seed, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout

    def setUp(self):
        """Prépare deux fichiers signés et des fichiers tiers dont les blobs doivent rester identiques. MECA-007."""
        self.temporary = tempfile.TemporaryDirectory(prefix="meca-tests-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.seed = self.root / "seed"
        self.seed.mkdir()
        self.remote = self.root / "remote.git"
        self.git("init", "--bare", str(self.remote))
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Test Account")
        self.git("config", "user.email", "test@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.git("remote", "add", "origin", str(self.remote))
        generate_keys(self.root / "keys")
        self.private, self.public = load_keys(self.root / "keys/signing.key", self.root / "keys/public-key.txt")
        self.original = encode(fixture())
        (self.seed / FILES[0]).write_bytes(self.original)
        (self.seed / FILES[1]).write_bytes(sign(self.original, self.private))
        (self.seed / "other").mkdir()
        (self.seed / "other/untouched.txt").write_bytes(b"private unrelated file\n")
        (self.seed / "README.md").write_text("Unrelated governance documentation\n", encoding="utf-8")
        (self.seed / ".gitattributes").write_text("*.json text eol=crlf\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-m", "fixture")
        self.git("push", "origin", "main")
        self.base = self.remote_git("rev-parse", "refs/heads/main").decode().strip()

    def remote_git(self, *args):
        """Lit la tête et les blobs du serveur bare réellement mis à jour. MECA-007."""
        return self.git("--git-dir=" + str(self.remote), *args)

    def repository(self):
        """Configure l'identité locale du commit sans dépendre du compte du poste. MECA-007."""
        return Repository(str(self.remote), "main", "Tool Test", "tool@example.invalid")

    def candidate(self):
        """Ajoute un second projet et prépare une unique nouvelle version signée. MECA-007."""
        p = renew(decode(self.original))
        second = deepcopy(p["projects"][0])
        second.update(projectId=43, sonarProjectKey="second-project")
        p["projects"].append(second)
        document = encode(p)
        return document, sign(document, self.private)

    def advance(self):
        """Simule une modification concurrente légitime dans un fichier tiers du dépôt. MECA-005, MECA-007."""
        (self.seed / "README.md").write_text("Another administrator changed this\n", encoding="utf-8")
        self.git("add", "README.md")
        self.git("commit", "-m", "concurrent")
        self.git("push", "origin", "main")

    def test_checkout_only_two_and_push_preserves_other_blobs(self):
        """Vérifie le contenu exact du checkout et du commit, y compris avec attributs CRLF distants. MECA-001, MECA-005, MECA-007."""
        before = {name: self.remote_git("rev-parse", "main:" + name) for name in ("README.md", "other/untouched.txt", ".gitattributes")}
        with self.repository() as repo:
            self.assertEqual(set(FILES), {p.name for p in repo.path.iterdir() if p.name != ".git"})
            original, signature = repo.read()
            self.assertEqual(self.original, original)
            verify(original, signature, self.public)
            document, detached = self.candidate()
            commit = repo.publish(document, detached, 8)
            self.assertEqual(commit, repo.remote_head())
        self.assertEqual(set(FILES), set(self.remote_git("diff", "--name-only", self.base, commit).decode().splitlines()))
        for name, blob in before.items():
            self.assertEqual(blob, self.remote_git("rev-parse", "main:" + name))
        stored = self.remote_git("show", "main:" + FILES[0])
        self.assertEqual(document, stored)
        verify(stored, self.remote_git("show", "main:" + FILES[1]), self.public)

    def test_rejects_preexisting_concurrent_update_and_preserves_draft(self):
        """Une tête déjà modifiée provoque un refus avant le commit, avec brouillon récupérable. MECA-005, MECA-007."""
        with self.repository() as repo:
            self.advance()
            document, detached = self.candidate()
            recovery = self.root / "recovery"
            recovery.mkdir()
            with patch("meca.repository.tempfile.mkdtemp", return_value=str(recovery)):
                with self.assertRaisesRegex(MecaError, "branche a changé"):
                    repo.publish(document, detached, 8)
            self.assertEqual(document, (recovery / FILES[0]).read_bytes())
            self.assertEqual(self.original, self.remote_git("show", "main:" + FILES[0]))

    def test_race_during_push_never_forces_remote_history(self):
        """Un concurrent après le contrôle initial fait échouer le push non fast-forward. MECA-005, MECA-007."""
        with self.repository() as repo:
            command = repo.git
            def race(*args, **kwargs):
                """Insère une modification distante juste avant la vraie commande push. MECA-007."""
                if args[0] == "push":
                    self.advance()
                return command(*args, **kwargs)
            recovery = self.root / "recovery"
            recovery.mkdir()
            with patch.object(repo, "git", side_effect=race), patch("meca.repository.tempfile.mkdtemp", return_value=str(recovery)):
                with self.assertRaisesRegex(MecaError, "push refusé"):
                    repo.publish(*self.candidate(), 8)
            self.assertEqual(self.original, self.remote_git("show", "main:" + FILES[0]))
            self.assertIn(b"Another administrator", self.remote_git("show", "main:README.md"))

    def test_missing_signature_and_symlink_are_refused(self):
        """Les fichiers manquants ou liens Git ne sont jamais traités comme une politique éditable. MECA-001, MECA-007."""
        self.git("rm", FILES[1])
        self.git("commit", "-m", "missing signature")
        self.git("push", "origin", "main")
        with self.assertRaisesRegex(MecaError, "deux fichiers réguliers"):
            with self.repository():
                self.fail("Should not checkout")
        self.git("checkout", self.base, "--", FILES[1])
        target = self.git("hash-object", "-w", "--stdin", data=b"other/untouched.txt").decode().strip()
        self.git("update-index", "--cacheinfo", f"120000,{target},{FILES[0]}")
        self.git("commit", "-m", "symlink")
        self.git("push", "origin", "main")
        with self.assertRaisesRegex(MecaError, "deux fichiers réguliers"):
            with self.repository():
                self.fail("Should not checkout")

    def test_cli_cancel_and_real_publication(self):
        """Le vrai point d'entrée vérifie les clés, annule sans push puis publie un renouvellement. MECA-004, MECA-007."""
        args = ["edit", "--repository", str(self.remote), "--gitlab-url", "https://gitlab.invalid", "--private-key", str(self.root / "keys/signing.key"), "--public-key", str(self.root / "keys/public-key.txt"), "--author-name", "Test", "--author-email", "test@example.invalid"]
        messages = []
        self.assertEqual(0, main(args, read=lambda _: "0", write=messages.append))
        self.assertEqual(self.base, self.remote_git("rev-parse", "main").decode().strip())
        responses = iter(["5", "PUBLIER"])
        self.assertEqual(0, main(args, read=lambda _: next(responses), write=messages.append))
        self.assertTrue(any("Publication confirmée" in line for line in messages))
        self.assertEqual(8, decode(self.remote_git("show", "main:" + FILES[0]))["policyVersion"])

    def test_invalid_signature_prevents_editor(self):
        """Une signature invalide ne permet pas à l'assistant de normaliser puis republier le document. MECA-003, MECA-007."""
        (self.seed / FILES[1]).write_text("invalid", encoding="ascii")
        self.git("add", FILES[1])
        self.git("commit", "-m", "invalid signature")
        self.git("push", "origin", "main")
        args = ["edit", "--repository", str(self.remote), "--gitlab-url", "https://gitlab.invalid", "--private-key", str(self.root / "keys/signing.key"), "--public-key", str(self.root / "keys/public-key.txt")]
        with patch("meca.cli.Wizard.edit") as editor:
            self.assertEqual(1, main(args, write=lambda _: None))
            editor.assert_not_called()

    def test_complete_wizard_multiple_projects_model_and_existing_edit(self):
        """Publie en une version plusieurs créations et une édition puis vérifie la signature distante. MECA-004, MECA-007."""
        args = ["edit", "--repository", str(self.remote), "--gitlab-url", "https://gitlab.invalid", "--private-key", str(self.root / "keys/signing.key"), "--public-key", str(self.root / "keys/public-key.txt"), "--author-name", "Test", "--author-email", "test@example.invalid"]
        answers = iter(["3", "new-alias", "MISTRAL", "new-model", "oui", "1", "43", "oui", "CHAT", "new-alias", "1000", "500", "-", "LOCAL_GOVERNED", "oui", "new-sonar", "1", "44", "non", "-", "coding", "0", "-", "-", "LOCAL_GOVERNED", "non", "-", "2", "42", "", "", "", "20000", "", "", "", "", "", "5", "PUBLIER"])
        messages = []
        self.assertEqual(0, main(args, read=lambda _: next(answers), write=messages.append))
        data = self.remote_git("show", "main:" + FILES[0])
        verify(data, self.remote_git("show", "main:" + FILES[1]), self.public)
        result = decode(data)
        self.assertEqual(8, result["policyVersion"])
        self.assertEqual(2, len(result["models"]))
        self.assertEqual([42, 43, 44], [p["projectId"] for p in result["projects"]])
        self.assertEqual(20000, result["projects"][0]["monthlyTokenQuota"])
        self.assertEqual("new-alias", result["projects"][1]["modelAlias"])
        self.assertEqual({*FILES}, set(self.remote_git("diff", "--name-only", self.base, "main").decode().splitlines()))

    def test_server_rejection_does_not_report_success_or_change_remote(self):
        """Un refus du serveur conserve la tête distante et un brouillon, sans contourner le hook. MECA-005, MECA-007."""
        hook = self.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nexit 1\n", encoding="utf-8", newline="\n")
        hook.chmod(0o700)
        with self.repository() as repo:
            recovery = self.root / "recovery"
            recovery.mkdir()
            with patch("meca.repository.tempfile.mkdtemp", return_value=str(recovery)):
                with self.assertRaisesRegex(MecaError, "push refusé"):
                    repo.publish(*self.candidate(), 8)
            self.assertEqual(self.base, repo.remote_head())
            self.assertTrue((recovery / FILES[0]).is_file())

    def test_no_tokens_in_urls_and_no_option_branches(self):
        """Refuse credentials intégrés, protocoles exécutables et arguments Git injectés. MECA-006, MECA-007."""
        for value in ("https://user:secret@gitlab.invalid/repo", "https://token@gitlab.invalid/repo", "http://gitlab.invalid/repo", "ext::command", "--upload-pack=bad"):
            with self.assertRaises(MecaError):
                repository_url(value)
        with self.assertRaises(MecaError):
            Repository(str(self.remote), "--help")
        with self.assertRaises(MecaError):
            with Repository(str(self.remote), "bad..branch"):
                self.fail("Should reject branch")


if __name__ == "__main__":
    unittest.main()
