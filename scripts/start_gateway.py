"""Start the delivered gateway with literal external configuration. Exigences : DEVAI-035, DEVAI-036, DEVAI-145."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


def environment(config, inherited):
    """Load key/value settings and whole-value environment references without shell evaluation. Exigences : DEVAI-035, DEVAI-036."""
    values = dict(inherited)
    if config.is_file():
        for line in config.read_text(encoding="utf-8-sig").splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            match = re.fullmatch(r"([A-Z][A-Z0-9_]*)=(.*)", line)
            if not match:
                raise ValueError("Invalid configuration line")
            key, value = match.groups()
            reference = re.fullmatch(r"\$\{([A-Z][A-Z0-9_]*)\}", value)
            if reference:
                value = values.get(reference[1], "")
                if not value:
                    raise ValueError(f"Required external value missing for {key}")
            values[key] = value
    return values


def command(install_root):
    """Require one delivered gateway JAR and keep executable paths separate from arguments. Exigences : DEVAI-145."""
    jars = sorted((install_root / "bin").glob("ods-gateway-*.jar"))
    if len(jars) != 1 or not jars[0].is_file():
        raise ValueError("Exactly one gateway JAR is required in bin")
    return ["java", "-jar", str(jars[0]), "--spring.config.additional-location=optional:file:./config/application-organisation.properties"]


def main():
    """Run Java directly and preserve its exit status while keeping credentials out of logs. Exigences : DEVAI-035, DEVAI-145."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", "-Config", type=Path)
    arguments = parser.parse_args()
    install_root = Path(__file__).resolve().parent.parent
    try:
        settings = environment(arguments.config or install_root / "config/gateway.env", os.environ)
        return subprocess.run(command(install_root), cwd=install_root, env=settings).returncode
    except (OSError, ValueError) as error:
        print(str(error) if isinstance(error, ValueError) else "Unable to start Java; check Java 21 and the delivery files.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
