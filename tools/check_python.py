"""Validate explanatory requirement comments using Python's real syntax tree. Exigences : DEVAI-137, DEVAI-138."""
import ast
from pathlib import Path
import re
import sys


def main() -> None:
    """Inspect all authored Python symbols and fail on missing or unknown DEVAI references. Exigences : DEVAI-138."""
    root = Path(__file__).resolve().parent.parent
    ids = set(re.findall(r"DEVAI-\d{3}", (root / "specification.md").read_text(encoding="utf-8")))
    failures = []
    files = [*root.joinpath("tools").rglob("*.py"), *root.joinpath("examples").rglob("*.py"), *root.joinpath("scripts").rglob("*.py")]
    for file in files:
        for node in ast.walk(ast.parse(file.read_text(encoding="utf-8"), filename=str(file.relative_to(root)))):
            if isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
                doc = ast.get_docstring(node) or ""
                refs = set(re.findall(r"DEVAI-\d{3}", doc))
                if len(doc) < 40 or not refs or not refs <= ids:
                    failures.append(f"{file.relative_to(root)}:{node.lineno}: documentation/reference invalid")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        raise SystemExit(1)
    print(f"Python documentation verified: {len(files)} source files.")


if __name__ == "__main__":
    main()
