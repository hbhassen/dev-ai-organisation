"""Security tests for the actual trusted scanner archive extraction. Exigences : DEVAI-054, DEVAI-085."""
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("scan_runner", Path(__file__).resolve().parents[2] / "examples/runner/scripts/scan_runner.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class RunnerTest(unittest.TestCase):
    """Use real ZIP inputs to verify extraction integrity and excluded credential files. Exigences : DEVAI-054."""

    def archive(self, entries):
        """Build an in-memory archive with the exact malicious or valid test paths. Exigences : DEVAI-054."""
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            for name, content in entries:
                archive.writestr(name, content)
        return stream.getvalue()

    def test_valid_source_and_secret_exclusion(self):
        """Preserve ordinary source while excluding an environment credential file. Exigences : DEVAI-037, DEVAI-085."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runner.extract_source(self.archive([("repo/src/main.py", "print('hello')"), ("repo/.env.production", "excluded")]), root)
            self.assertEqual((root / "src/main.py").read_text(), "print('hello')")
            self.assertFalse((root / ".env.production").exists())

    def test_traversal_is_rejected(self):
        """Refuse both Unix parent traversal and Windows separator traversal. Exigences : DEVAI-054."""
        with tempfile.TemporaryDirectory() as directory:
            for path in ["repo/../../escape", "repo/a\\..\\escape"]:
                with self.assertRaises(ValueError):
                    runner.extract_source(self.archive([(path, "bad")]), Path(directory))

    def test_case_collision_is_rejected(self):
        """Reject duplicate case-folded targets before a cross-platform overwrite can occur. Exigences : DEVAI-054."""
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                runner.extract_source(self.archive([("repo/A.py", "first"), ("repo/a.py", "second")]), Path(directory))


if __name__ == "__main__":
    unittest.main()
