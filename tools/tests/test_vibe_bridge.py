"""Exercise bridge lifecycle and safe failures without any model or network calls. Exigences : DEVAI-170, DEVAI-176, DEVAI-177."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("tested_vibe_bridge", Path(__file__).resolve().parents[2] / "scripts/vibe_bridge.py")
bridge = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bridge)


class VibeBridgeTest(unittest.TestCase):
    """Cover the real stdin entry point and Windows temporary-directory ownership. Exigences : DEVAI-170, DEVAI-177."""

    def invoke(self, error=None):
        """Replace only completion while retaining real cwd changes, temporary cleanup and JSON framing. Exigences : DEVAI-170, DEVAI-176."""
        original = Path.cwd()
        visited = []

        async def complete(request):
            """Observe isolation at the model boundary and optionally inject a private failure. Exigences : DEVAI-176, DEVAI-177."""
            visited.append(Path.cwd())
            self.assertEqual(str(Path.cwd()), os.environ["VIBE_HOME"])
            if error:
                raise error
            return {"choices": [{"message": {"content": "synthetic answer"}}]}

        output, errors = io.StringIO(), io.StringIO()
        with patch.object(bridge, "complete", new=complete), patch.object(sys, "stdin", io.TextIOWrapper(io.BytesIO(b"{}"))), patch.object(sys, "path", sys.path.copy()), patch.dict(os.environ), patch.object(bridge.logging, "disable"), contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            status = bridge.main()
        self.assertEqual(original, Path.cwd(), "Leave the temporary cwd before attempting deletion")
        self.assertTrue(visited)
        self.assertFalse(visited[0].exists(), "The isolated Vibe home must be removed")
        self.assertNotIn("synthetic-private", output.getvalue() + errors.getvalue())
        return status, json.loads(output.getvalue())

    def test_success_survives_temporary_directory_cleanup(self):
        """Keep a successful completion successful after actual Windows filesystem cleanup. Exigences : DEVAI-170, DEVAI-177."""
        status, result = self.invoke()
        self.assertEqual(0, status)
        self.assertEqual("synthetic answer", result["choices"][0]["message"]["content"])

    def test_failures_restore_cwd_and_never_expose_provider_details(self):
        """Classify provider status without disclosing credentials or blaming Python for HTTP denial. Exigences : DEVAI-170, DEVAI-176."""
        for http_status, code in [(401, "MODEL_AUTHENTICATION_FAILED"), (403, "MODEL_AUTHENTICATION_FAILED"), (429, "MODEL_RATE_LIMITED"), (404, "MODEL_UNAVAILABLE"), (500, "VIBE_REQUEST_FAILED")]:
            with self.subTest(status=http_status):
                error = RuntimeError("synthetic-private provider body")
                error.status = http_status
                status, result = self.invoke(error)
                self.assertEqual(1, status)
                self.assertEqual({"error": {"code": code}}, result)
        status, result = self.invoke(ImportError("synthetic-private runtime path"))
        self.assertEqual(1, status)
        self.assertEqual("VIBE_RUNTIME_UNAVAILABLE", result["error"]["code"])


if __name__ == "__main__":
    unittest.main()
