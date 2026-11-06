"""Verify current-version private VSIX installation without launching the editor. Exigences : DEVAI-145, DEVAI-167, DEVAI-177."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('vscode_bench', Path(__file__).resolve().parents[2] / 'TestDOKERMan/vscode.py')
bench = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)


class VscodeBenchTest(unittest.TestCase):
    """Keep installation aligned with the manifest version and refuse absent packages. Exigences : DEVAI-145, DEVAI-167, DEVAI-177."""

    def test_install_uses_current_version_and_rejects_missing_package(self):
        """Select the manifest's private artifact and never silently fall back to an older VSIX. Exigences : DEVAI-145, DEVAI-177."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'vscode-extension').mkdir()
            (root / 'vscode-extension/package.json').write_text(json.dumps({'version':'7.8.9'}), encoding='utf8')
            (root / '.artifacts').mkdir()
            package = root / '.artifacts/ods-docker-manual-7.8.9.vsix'
            with patch.object(bench, 'ROOT', root / 'TestDOKERMan'), patch.object(bench, 'LOCAL', root / 'TestDOKERMan/.local'), patch.object(bench, 'executable', return_value=root / 'editor/Code.exe'), patch.object(bench.subprocess, 'run') as run, patch('sys.argv', ['vscode.py', 'install']):
                with self.assertRaises(RuntimeError):
                    bench.main()
                run.assert_not_called()
                package.write_bytes(b'synthetic-package')
                bench.main()
                command = run.call_args.args[0]
                self.assertEqual(str(package), command[command.index('--install-extension') + 1])
                self.assertEqual('Test', command[command.index('--profile') + 1])
                self.assertTrue(run.call_args.kwargs['check'])

    def test_open_starts_a_new_test_window_with_the_bench_jdk(self):
        """Activate the newly installed extension in a fresh window using only the isolated test environment. Exigences : DEVAI-145, DEVAI-167, DEVAI-177."""
        with tempfile.TemporaryDirectory() as directory:
            local = Path(directory)
            with patch.object(bench, 'LOCAL', local), patch.object(bench, 'executable', return_value=local / 'editor/Code.exe'), patch.object(bench.subprocess, 'Popen') as launch, patch('sys.argv', ['vscode.py', 'open']), patch('builtins.print'):
                bench.main()
                command = launch.call_args.args[0]
                self.assertIn('--new-window', command)
                self.assertEqual(str(local / 'workspaces/demo-allowed'), command[-1])
                self.assertEqual(str(local / 'jdk'), launch.call_args.kwargs['env']['JAVA_HOME'])
