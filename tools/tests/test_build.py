"""Check delivery preservation, integrity and process failures for the Python build. Exigences : DEVAI-035, DEVAI-140, DEVAI-144, DEVAI-145."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build
import maven_wrapper

spec = importlib.util.spec_from_file_location("start_gateway", Path(__file__).resolve().parents[2] / "scripts/start_gateway.py")
gateway = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gateway)


class BuildTest(unittest.TestCase):
    """Verify failure propagation and generated-package safety with actual temporary files. Exigences : DEVAI-140, DEVAI-144, DEVAI-145."""

    def test_failed_command_stops_the_build(self):
        """Preserve a real subprocess failure code instead of treating a failed build as success. Exigences : DEVAI-140."""
        with self.assertRaises(subprocess.CalledProcessError) as failure:
            build.run([sys.executable, "-c", "raise SystemExit(7)"])
        self.assertEqual(7, failure.exception.returncode)

    def test_private_package_rejects_accidental_dotenv(self):
        """Permit the intentional deployment document but reject any additional hidden dotenv file. Exigences : DEVAI-167, DEVAI-176."""
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory)
            runtime = stage / "runtime"
            runtime.mkdir()
            (runtime / "deployment.json").write_text('{"ODS_TOKEN":"synthetic-only"}')
            build.validate_private_package(stage)
            for name in (".env", ".env.local"):
                hidden = runtime / name
                hidden.write_text("UNRELATED_SECRET=synthetic-only")
                with self.assertRaisesRegex(ValueError, "dotenv"):
                    build.validate_private_package(stage)
                hidden.unlink()

    def test_source_identity_changes_cannot_be_released(self):
        """Reject a changed commit and retain dirty state even if files become clean during the build. Exigences : DEVAI-144, DEVAI-145."""
        source = {"sourceCommit": "a" * 40, "sourceDirty": True}
        self.assertTrue(build.delivery_identity(source, source | {"sourceDirty": False})["sourceDirty"])
        with self.assertRaisesRegex(ValueError, "commit changed"):
            build.delivery_identity(source, source | {"sourceCommit": "b" * 40})

    def test_previous_delivery_is_preserved(self):
        """Preserve user files and old artifacts in a complete snapshot before rebuilding a suite. Exigences : DEVAI-144, DEVAI-145."""
        with tempfile.TemporaryDirectory(prefix="ods build ") as directory:
            root = Path(directory)
            (root / "suiteInstall/bin").mkdir(parents=True)
            (root / "suiteInstall/bin/old.jar").write_bytes(b"previous")
            (root / "suiteInstall/local-notes.txt").write_text("keep")
            suite = build.preserve_suite(root)
            self.assertEqual([], list(suite.iterdir()))
            previous, = (root / ".artifacts").glob("previous-suite-*")
            self.assertEqual(b"previous", (previous / "bin/old.jar").read_bytes())
            self.assertEqual("keep", (previous / "local-notes.txt").read_text())

    def test_locked_delivery_directory_has_a_complete_backup(self):
        """Retain the existing locked-directory fallback while removing old files from the new delivery. Exigences : DEVAI-145."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "suiteInstall/bin").mkdir(parents=True)
            (root / "suiteInstall/bin/old.jar").write_bytes(b"old")
            with patch.object(Path, "rename", side_effect=PermissionError("locked")):
                suite = build.preserve_suite(root)
            previous, = (root / ".artifacts").glob("previous-suite-*")
            self.assertEqual(b"old", (previous / "bin/old.jar").read_bytes())
            self.assertFalse((suite / "bin/old.jar").exists())

    def test_maven_archive_hash_and_paths_are_verified(self):
        """Accept a pinned distribution and reject corrupt data or an archive escaping its cache. Exigences : DEVAI-035, DEVAI-144."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "maven.zip"
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("apache-maven-test/bin/mvn.cmd", "@echo off")
            checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
            maven_wrapper.extract_verified(archive, root / "ok", checksum)
            self.assertTrue((root / "ok/apache-maven-test/bin/mvn.cmd").is_file())
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                maven_wrapper.extract_verified(archive, root / "corrupt", "0" * 64)
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("../escape.txt", "outside")
            with self.assertRaisesRegex(ValueError, "Unsafe"):
                maven_wrapper.extract_verified(archive, root / "unsafe", hashlib.sha256(archive.read_bytes()).hexdigest())
            self.assertFalse((root / "escape.txt").exists())

    def test_vsix_must_contain_the_built_engine_and_version(self):
        """Reject stale engine bytes or a different release version even when VSIX entries exist. Exigences : DEVAI-141, DEVAI-145."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            engine = root / "engine.jar"
            engine.write_bytes(b"current engine")
            vsix = root / "extension.vsix"
            with zipfile.ZipFile(vsix, "w") as archive:
                archive.writestr("extension/package.json", json.dumps({"version": "0.2.1"}))
                archive.writestr("extension/dist/extension.js", "module.exports = {}")
                archive.writestr("extension/media/view.js", "// view")
                archive.writestr("extension/engine/ods-java-core.jar", engine.read_bytes())
                archive.writestr("extension/engine/ods-java-core.jar.sha256", build.digest(engine))
                runtime = {name: b"synthetic runtime file" for name in ("ods-gateway.jar", "deployment.json", "vibe_bridge.py", "local_analysis.py")}
                for name, content in runtime.items():
                    archive.writestr("extension/runtime/" + name, content)
                archive.writestr("extension/runtime/INTEGRITY.json", json.dumps({name: hashlib.sha256(content).hexdigest() for name, content in runtime.items()}))
            build.verify_vsix(vsix, "0.2.1", engine)
            with self.assertRaisesRegex(ValueError, "version mismatch"):
                build.verify_vsix(vsix, "0.2.2", engine)
            engine.write_bytes(b"new engine")
            with self.assertRaisesRegex(ValueError, "digest mismatch"):
                build.verify_vsix(vsix, "0.2.1", engine)


class GatewayLauncherTest(unittest.TestCase):
    """Preserve literal gateway configuration and strict startup preconditions. Exigences : DEVAI-035, DEVAI-036, DEVAI-145."""

    def test_configuration_is_literal_and_external_values_are_required(self):
        """Keep shell metacharacters and Unicode as data while rejecting missing secret references. Exigences : DEVAI-035, DEVAI-036."""
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "gateway.env"
            literal = 'x&whoami|echo !value! %PATH% "quoted" = accent\u00e9'
            config.write_text("# Comment\n\nODS_LITERAL=" + literal + "\nODS_TOKEN=${EXTERNAL_TEST_TOKEN}\n", encoding="utf8")
            inherited = {"EXTERNAL_TEST_TOKEN": "synthetic-test-only"}
            actual = gateway.environment(config, inherited)
            self.assertEqual(literal, actual["ODS_LITERAL"])
            self.assertEqual("synthetic-test-only", actual["ODS_TOKEN"])
            self.assertNotIn("ODS_LITERAL", inherited)
            with self.assertRaisesRegex(ValueError, "Required external"):
                gateway.environment(config, {})
            config.write_text("invalid setting\n", encoding="utf8")
            with self.assertRaisesRegex(ValueError, "Invalid configuration"):
                gateway.environment(config, {})

    def test_exactly_one_gateway_is_required_and_spaces_are_preserved(self):
        """Select one JAR as a single argument and refuse ambiguous or missing distributions. Exigences : DEVAI-145."""
        with tempfile.TemporaryDirectory(prefix="ods delivery ") as directory:
            root = Path(directory)
            (root / "bin").mkdir()
            with self.assertRaisesRegex(ValueError, "Exactly one"):
                gateway.command(root)
            jar = root / "bin/ods-gateway-0.2.1.jar"
            jar.write_bytes(b"jar")
            self.assertEqual(str(jar), gateway.command(root)[2])
            (root / "bin/ods-gateway-old.jar").write_bytes(b"old")
            with self.assertRaisesRegex(ValueError, "Exactly one"):
                gateway.command(root)


if __name__ == "__main__":
    unittest.main()
