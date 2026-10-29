"""Check organisation branding and secret separation on actual staged files. Exigences : DEVAI-035, DEVAI-127, DEVAI-144."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("organisation_profile", Path(__file__).resolve().parents[1] / "organisation_profile.py")
profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profile)


class ProfileTest(unittest.TestCase):
    """Exercise strict configuration and packaging boundaries with synthetic credentials. Exigences : DEVAI-035, DEVAI-127."""

    def fixture(self, root):
        """Create a complete small profile using only a synthetic provider credential. Exigences : DEVAI-127."""
        data = {"schemaVersion": 1, "id": "example-org", "extension": {"name": "example-tools", "publisher": "example-org", "displayName": "Organisation Exemple"}, "gatewayUrl": "https://gateway.example.test", "envFiles": ["gateway.env"], "environment": {"ODS_MODEL_ALIAS": "approved"}, "files": [{"source": "mapping.properties", "target": "application-organisation.properties"}]}
        path = root / "organisation.json"
        path.write_text(json.dumps(data), encoding="utf8")
        (root / "gateway.env").write_text("ODS_MISTRAL_API_KEY=synthetic-integration-only\nODS_MODEL_ALIAS=old\n", encoding="utf8")
        (root / "mapping.properties").write_text("ods.sonar-project-1=example\n", encoding="utf8")
        return path, data

    def test_branding_and_server_secret_separation(self):
        """Keep public branding and delivery examples separate from credentials embedded later by the runtime assembler. Exigences : DEVAI-035, DEVAI-144, DEVAI-167, DEVAI-176."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config, _ = self.fixture(root)
            extension = root / "vscode-extension"
            extension.mkdir()
            for name in ("dist", "media", "engine"):
                (extension / name).mkdir()
                (extension / name / "sample").write_text("sample", encoding="utf8")
            manifest = Path(__file__).resolve().parents[2] / "vscode-extension/package.json"
            (extension / "package.json").write_bytes(manifest.read_bytes())
            profile.stage(root, root / "staged", config)
            staged = json.loads((root / "staged/package.json").read_text(encoding="utf8"))
            self.assertEqual("example-tools", staged["name"])
            self.assertEqual("Organisation Exemple", staged["contributes"]["views"]["ods"][0]["name"])
            self.assertNotIn("synthetic-integration-only", "".join(p.read_text(encoding="utf8") for p in (root / "staged").rglob("*") if p.is_file()))
            profile.deliver(config, root / "delivery")
            self.assertIn("ODS_MODEL_ALIAS=approved", (root / "delivery/gateway.env").read_text())
            self.assertNotIn("synthetic-integration-only", (root / "delivery/gateway.env").read_text())
            self.assertIn("ODS_MISTRAL_API_KEY=<ODS_MISTRAL_API_KEY>", (root / "delivery/gateway.env").read_text())
            self.assertTrue((root / "delivery/application-organisation.properties").exists())

    def test_reject_escaping_files_unknown_fields_and_unsafe_urls(self):
        """Reject malformed profiles before compilation or copying any organisation inputs. Exigences : DEVAI-036, DEVAI-127."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path, data = self.fixture(root)
            for mutation in ({"envFiles": ["../outside.env"]}, {"gatewayUrl": "http://remote.example.test"}, {"unknown": "ignored?"}, {"environment": {"JAVA_TOOL_OPTIONS": "unwanted"}}, {"files": [{"source": "mapping.properties", "target": "../../escape"}]}):
                path.write_text(json.dumps(data | mutation), encoding="utf8")
                with self.assertRaises(ValueError):
                    profile.load(path)

    def test_staging_never_overwrites_an_existing_directory(self):
        """Keep previous builds intact when a caller accidentally reuses a staging path. Exigences : DEVAI-144."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(FileExistsError):
                profile.stage(root, root)


if __name__ == "__main__":
    unittest.main()
