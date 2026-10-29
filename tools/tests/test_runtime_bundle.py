"""Check immutable build choices and explicit secret resolution without using workstation credentials. Exigences : DEVAI-167, DEVAI-171, DEVAI-177."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import runtime_bundle


class RuntimeBundleTest(unittest.TestCase):
    """Test build boundaries separately from the official Vibe transport contract. Exigences : DEVAI-167, DEVAI-177."""

    def test_explicit_resolution_defaults_and_rejections(self):
        """Resolve only explicitly referenced secrets and reject invalid administrator choices. Exigences : DEVAI-167, DEVAI-176."""
        result = runtime_bundle.resolve({"ODS_MISTRAL_API_KEY": "${BUILD_TEST_KEY}"}, {"BUILD_TEST_KEY": "synthetic-only", "UNRELATED_TOKEN": "not-copied"})
        self.assertEqual("VIBE_CLI", result["ODS_LLM_TRANSPORT"])
        self.assertEqual("synthetic-only", result["ODS_MISTRAL_API_KEY"])
        self.assertNotIn("UNRELATED_TOKEN", result)
        for config in ({"ODS_MISTRAL_API_KEY": "${MISSING}"}, {"ODS_LLM_TRANSPORT": "unknown"}, {"ODS_WORKSPACE": "other-root"}, {"ODS_LOCAL_TOKEN": "override"}):
            with self.assertRaises(ValueError):
                runtime_bundle.resolve(config, {})

    def test_api_package_embeds_private_config_and_hashes(self):
        """Verify the accepted private-VSIX secret placement without altering any source input file. Exigences : DEVAI-167, DEVAI-176."""
        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            gateway = temporary / "test.jar"
            gateway.write_bytes(b"synthetic-gateway")
            runtime_bundle.prepare(root, temporary / "runtime", gateway, {"ODS_LLM_TRANSPORT": "API", "ODS_MISTRAL_API_KEY": "synthetic-only"}, "example")
            config = json.loads((temporary / "runtime/deployment.json").read_text(encoding="utf8"))
            self.assertEqual("synthetic-only", config["environment"]["ODS_MISTRAL_API_KEY"])
            inventory = json.loads((temporary / "runtime/INTEGRITY.json").read_text())
            self.assertIn("ods-gateway.jar", inventory)
            self.assertIn("local_analysis.py", inventory)
            self.assertFalse((temporary / "runtime/vibe").exists())

    def test_long_sdk_filenames_are_hashed_without_truncation(self):
        """Cover official SDK model paths exceeding the legacy Windows 260-character limit. Exigences : DEVAI-006, DEVAI-171."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / ("runtime-" + "r" * 70)
            root.mkdir()
            relative = "model-" + "m" * 160 + ".py"
            target = runtime_bundle.extended(root / relative)
            target.write_bytes(b"synthetic long SDK model")
            try:
                runtime_bundle.inventory(root)
                inventory = json.loads((root / "INTEGRITY.json").read_text())
                self.assertIn(relative, inventory)
                self.assertEqual(64, len(inventory[relative]))
            finally:
                target.unlink()


if __name__ == "__main__":
    unittest.main()
