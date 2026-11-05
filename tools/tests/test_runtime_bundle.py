"""Check immutable build choices and explicit secret resolution without using workstation credentials. Exigences : DEVAI-167, DEVAI-171, DEVAI-177."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import organisation_profile
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

    def test_pinned_runtime_stage_uses_hardlinks_without_changing_bytes(self):
        """Stage immutable dependency bytes by hard link on the same volume. Exigences : DEVAI-171."""
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.bin"
            destination = Path(directory) / "stage.bin"
            source.write_bytes(b"pinned runtime bytes")
            runtime_bundle.link_or_copy(source, destination)
            self.assertTrue(destination.is_file())
            self.assertEqual(source.read_bytes(), destination.read_bytes())
            self.assertTrue(Path.samefile(source, destination))

    def test_pinned_runtime_stage_falls_back_when_hardlinks_are_unavailable(self):
        """Retain exact runtime bytes on filesystems that reject hard links. Exigences : DEVAI-171."""
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.bin"
            destination = Path(directory) / "stage.bin"
            source.write_bytes(b"pinned runtime bytes")
            with patch("runtime_bundle.os.link", side_effect=OSError("hard links unavailable")):
                runtime_bundle.link_or_copy(source, destination)
            self.assertEqual(source.read_bytes(), destination.read_bytes())
            self.assertFalse(Path.samefile(source, destination))

    def test_pinned_runtime_tree_stages_files_concurrently_and_keeps_copy_filters(self):
        """Stage the exact pinned tree while excluding only runtime-irrelevant files. Exigences : DEVAI-171."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "cache"
            (source / "package" / "bin").mkdir(parents=True)
            (source / "package" / "__pycache__").mkdir()
            (source / "package" / "module.py").write_bytes(b"runtime module")
            (source / "package" / "bin" / "launcher.exe").write_bytes(b"excluded bin")
            (source / "package" / "__pycache__" / "module.pyc").write_bytes(b"excluded bytecode")
            destination = root / "stage"
            runtime_bundle.copy_pinned_tree(source, destination)
            staged = destination / "package" / "module.py"
            self.assertEqual(b"runtime module", staged.read_bytes())
            self.assertTrue(Path.samefile(source / "package" / "module.py", staged))
            self.assertFalse((destination / "package" / "bin").exists())
            self.assertFalse((destination / "package" / "__pycache__").exists())

    def test_dual_provider_profile_resolves_both_external_credentials(self):
        """Resolve both provider credentials while keeping endpoint policy explicit and secret values external. Exigences : DEVAI-167, DEVAI-189, DEVAI-191, DEVAI-195, DEVAI-196."""
        root = Path(__file__).resolve().parents[2]
        profile, environment = organisation_profile.load(root / "config/organisations/mistral-openai/organisation.json")
        registry = environment["ODS_PROVIDER_REGISTRY"]
        resolved = runtime_bundle.resolve(environment, {
            "MISTRAL_API_KEY": "synthetic-mistral-only",
            "OPENAI_API_KEY": "synthetic-openai-only",
            "MISTRAL_MODEL": "synthetic-mistral-model",
        })
        self.assertEqual("organisation-mistral-openai", profile["id"])
        self.assertEqual("synthetic-mistral-only", resolved["ODS_MISTRAL_API_KEY"])
        self.assertEqual("synthetic-openai-only", resolved["ODS_OPENAI_API_KEY"])
        self.assertEqual("synthetic-mistral-model", resolved["ODS_MISTRAL_MODEL"])
        self.assertEqual(registry, resolved["ODS_PROVIDER_REGISTRY"])
        self.assertIn('"credentialRef":"ODS_OPENAI_API_KEY"', registry)
        for inherited in ({"MISTRAL_API_KEY": "synthetic", "MISTRAL_MODEL": "synthetic-model"}, {"OPENAI_API_KEY": "synthetic", "MISTRAL_MODEL": "synthetic-model"}):
            with self.assertRaises(ValueError):
                runtime_bundle.resolve(environment, inherited)

    def test_dual_provider_delivery_redacts_references(self):
        """Keep dual-provider references out of delivered example credentials. Exigences : DEVAI-145, DEVAI-167, DEVAI-176."""
        root = Path(__file__).resolve().parents[2]
        profile_path = root / "config/organisations/mistral-openai/organisation.json"
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory)
            organisation_profile.deliver(profile_path, destination)
            delivered = (destination / "gateway.env").read_text(encoding="utf8")
            self.assertIn("ODS_MISTRAL_API_KEY=<ODS_MISTRAL_API_KEY>", delivered)
            self.assertIn("ODS_OPENAI_API_KEY=<ODS_OPENAI_API_KEY>", delivered)
            self.assertNotIn("${MISTRAL_API_KEY}", delivered)
            self.assertNotIn("${OPENAI_API_KEY}", delivered)

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

    def test_pinned_runtime_tree_propagates_missing_source_and_worker_failure(self):
        """Refuse incomplete runtimes when discovery or a concurrent file operation fails. Exigences : DEVAI-171."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(OSError):
                runtime_bundle.copy_pinned_tree(root / "missing", root / "missing-stage")
            source = root / "cache"
            source.mkdir()
            (source / "module.py").write_bytes(b"required runtime module")
            with patch("runtime_bundle.link_or_copy", side_effect=OSError("synthetic staging failure")):
                with self.assertRaises(OSError):
                    runtime_bundle.copy_pinned_tree(source, root / "failed-stage")


if __name__ == "__main__":
    unittest.main()
