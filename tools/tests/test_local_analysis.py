"""Verify local scanners with deterministic command and provider doubles, without organisational network access. Exigences : DEVAI-172, DEVAI-174, DEVAI-175, DEVAI-177."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("local_analysis", Path(__file__).resolve().parents[2] / "scripts/local_analysis.py")
analysis = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(analysis)


class LocalAnalysisTest(unittest.TestCase):
    """Exercise four actual generator command contracts and local source provenance with no commit requirement. Exigences : DEVAI-172, DEVAI-174, DEVAI-177."""

    def test_fingerprint_uncommitted_sources_and_deleted_files(self):
        """Detect edited and untracked sources while ignoring generated output and credentials. Exigences : DEVAI-175."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            subprocess.run(["git", "init", "--quiet", str(root)], check=True)
            source = root / "App.java"
            source.write_text("class A {}", encoding="utf8")
            before = analysis.fingerprint(root)
            source.write_text("class B {}", encoding="utf8")
            self.assertNotEqual(before, analysis.fingerprint(root))
            current = analysis.fingerprint(root)
            (root / ".env").write_text("SYNTHETIC=private", encoding="utf8")
            (root / "target").mkdir()
            (root / "target/result").write_text("output", encoding="utf8")
            self.assertEqual(current, analysis.fingerprint(root))
            subprocess.run(["git", "add", "App.java"], cwd=root, check=True)
            source.unlink()
            self.assertNotEqual(current, analysis.fingerprint(root))

    def test_all_sbom_generators_produce_downloadable_cyclonedx(self):
        """Assert command names and resolved-lock requirements while faking only the external tools. Exigences : DEVAI-172, DEVAI-177."""
        for stack, expected in {"JAVA": "mvn", "ANGULAR": "npx", "PYTHON": "cyclonedx-py", "DOTNET": "dotnet"}.items():
            with self.subTest(stack=stack), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                output = root / "out"
                output.mkdir()
                for name in ["pom.xml", "package-lock.json", "requirements.txt", "sample.csproj"]:
                    (root / name).touch()
                calls = []

                def generate(arguments, cwd, extra=None):
                    """Produce a small valid result only at the agreed scanner output location. Exigences : DEVAI-177."""
                    calls.append(arguments)
                    (output / "bom.json").write_text(json.dumps({"bomFormat": "CycloneDX", "specVersion": "1.6", "components": []}), encoding="utf8")

                with patch.object(analysis, "run", side_effect=generate):
                    result = analysis.sbom(root, stack, output)
                self.assertEqual(expected, calls[0][0])
                self.assertEqual("CycloneDX", result["sbom"]["bomFormat"])

    def test_sonar_credentials_never_enter_command_arguments(self):
        """Verify .NET begin/end credentials and actual CE metadata, including a credential-free build. Exigences : DEVAI-174, DEVAI-176, DEVAI-177."""
        for stack in ("JAVA", "ANGULAR", "PYTHON", "DOTNET"):
            with self.subTest(stack=stack), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / "sample.csproj").touch()
                calls = []

                def scan(arguments, cwd, extra=None):
                    """Simulate scanner metadata rather than pretending an existing remote report ran a scan. Exigences : DEVAI-177."""
                    calls.append((arguments, extra))
                    (root / "report-task.txt").write_text("ceTaskId=ce-synthetic\nprojectKey=managed-project\n", encoding="utf8")

                with patch.object(analysis, "run", side_effect=scan):
                    result = analysis.sonar(root, stack, root, {"url": "https://sonar.example.test", "token": "synthetic-service-secret", "projectKey": "managed-project"})
                self.assertEqual("ce-synthetic", result["ceTaskId"])
                for args, env in calls:
                    self.assertNotIn("synthetic-service-secret", str(args))
                    if "build" in args or "verify" in args:
                        self.assertIsNone(env)
                if stack == "DOTNET":
                    self.assertEqual(calls[0][1], calls[-1][1])
                    self.assertIn("sonar.token", calls[0][1]["SONARQUBE_SCANNER_PARAMS"])

    def test_changes_during_generation_are_refused(self):
        """Never bind a successful scan to inputs that changed while its process was running. Exigences : DEVAI-175."""
        with tempfile.TemporaryDirectory() as directory, patch.object(analysis, "fingerprint", side_effect=["before", "after"]), patch.object(analysis, "sbom", return_value={"sbom": {}}):
            with self.assertRaisesRegex(ValueError, "Sources changed"):
                analysis.execute({"operation": "sbom", "root": directory, "stack": "JAVA"})

    def test_service_environment_is_not_inherited(self):
        """Strip unrelated model, governance, proxy and credential environment variables from scanner children. Exigences : DEVAI-176."""
        with patch.dict(analysis.os.environ, {"ODS_GOVERNANCE_TOKEN": "synthetic", "MISTRAL_API_KEY": "synthetic", "HTTPS_PROXY": "https://user:secret@example.test"}):
            result = analysis.clean_environment()
            self.assertNotIn("ODS_GOVERNANCE_TOKEN", result)
            self.assertNotIn("MISTRAL_API_KEY", result)
            self.assertNotIn("HTTPS_PROXY", result)

    def test_scanner_timeout_terminates_its_owned_process_tree(self):
        """Ensure a timed-out command cannot outlive its scanner parent as an ordinary orphan build. Exigences : DEVAI-168, DEVAI-176."""
        with patch.object(analysis.shutil, "which", return_value="synthetic-scanner"), patch.object(analysis.subprocess, "Popen") as launch, patch.object(analysis.subprocess, "run") as terminate:
            child = launch.return_value
            child.pid = 123456
            child.wait.side_effect = [subprocess.TimeoutExpired("synthetic", 900), 1]
            if analysis.os.name == "nt":
                with self.assertRaises(subprocess.TimeoutExpired):
                    analysis.run(["synthetic-scanner"], Path.cwd())
                self.assertEqual(["taskkill.exe", "/PID", "123456", "/T", "/F"], terminate.call_args.args[0])
            else:
                with patch.object(analysis.os, "killpg") as kill, self.assertRaises(subprocess.TimeoutExpired):
                    analysis.run(["synthetic-scanner"], Path.cwd())
                kill.assert_called_once_with(123456, analysis.signal.SIGKILL)
            child.kill.assert_called_once()


if __name__ == "__main__":
    unittest.main()
