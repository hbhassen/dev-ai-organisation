"""Package the owned gateway and pinned Vibe distribution without modifying source configuration. Exigences : DEVAI-167, DEVAI-171, DEVAI-176."""
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys

VIBE_VERSION = "3.0.3"


def python_command():
    """Select an actual Python 3.12 runtime for the platform-specific Vibe dependencies. Exigences : DEVAI-171."""
    candidates = [[sys.executable], ["py", "-3.12"], ["python3.12"]] if os.name == "nt" else [[sys.executable], ["python3.12"]]
    for candidate in candidates:
        try:
            result = subprocess.run([*candidate, "-c", "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"], capture_output=True, text=True, timeout=10)
            if result.returncode == 0 and result.stdout.strip() == "3.12":
                return candidate
        except (OSError, subprocess.TimeoutExpired):
            continue
    raise ValueError("Python 3.12 is required to package the pinned Vibe runtime")


def resolve(environment, inherited=None):
    """Resolve explicit build-secret references without printing values or importing unrelated host credentials. Exigences : DEVAI-167, DEVAI-176."""
    inherited = os.environ if inherited is None else inherited
    result = dict(environment)
    for key, value in result.items():
        match = re.fullmatch(r"\$\{([A-Z][A-Z0-9_]*)\}", value)
        if match:
            if not inherited.get(match[1]):
                raise ValueError(f"Missing build-time external value for {key}")
            result[key] = inherited[match[1]]
    result.setdefault("ODS_LLM_TRANSPORT", "VIBE_CLI")
    result.setdefault("ODS_VIBE_AUTH", "API_KEY")
    result.setdefault("ODS_VIBE_URL", result.get("ODS_MISTRAL_URL", "https://api.mistral.ai").rstrip("/") + "/v1")
    result.setdefault("ODS_JFROG_AUTH", "TOKEN")
    for key, choices in {"ODS_LLM_TRANSPORT": {"API", "VIBE_CLI"}, "ODS_VIBE_AUTH": {"API_KEY", "CLI_TOKEN"}, "ODS_JFROG_AUTH": {"BASIC", "TOKEN"}}.items():
        if result[key] not in choices:
            raise ValueError(f"Invalid build choice: {key}")
    forbidden = {"ODS_LOCAL_TOKEN", "ODS_EMBEDDED", "ODS_RUNTIME_DIRECTORY", "ODS_PYTHON_COMMAND", "ODS_WORKSPACE", "ODS_REPOSITORY"}
    if forbidden.intersection(result):
        raise ValueError("Runtime-owned fields cannot be supplied in an organisation profile")
    return result


def prepare(root, destination, gateway, environment, profile_id):
    """Assemble an isolated target-platform runtime with exact hashes and original third-party licenses. Exigences : DEVAI-141, DEVAI-167, DEVAI-171."""
    destination = extended(destination)
    destination.mkdir(parents=True, exist_ok=False)
    settings = resolve(environment)
    shutil.copy2(gateway, destination / "ods-gateway.jar")
    (destination / "ods-gateway.jar.sha256").write_text(hashlib.sha256(gateway.read_bytes()).hexdigest(), encoding="utf8")
    for name in ("vibe_bridge.py", "local_analysis.py"):
        shutil.copy2(root / "scripts" / name, destination / name)
    if settings["ODS_LLM_TRANSPORT"] == "VIBE_CLI":
        cache = root / ".artifacts" / f"vibe-{VIBE_VERSION}"
        metadata = cache / f"mistral_vibe-{VIBE_VERSION}.dist-info/METADATA"
        if not metadata.is_file():
            command = python_command()
            subprocess.run([*command, "-m", "pip", "install", "--disable-pip-version-check", "--no-compile", "--no-warn-script-location", "--target", str(cache), f"mistral-vibe=={VIBE_VERSION}"], check=True)
        shutil.copytree(cache, destination / "vibe", ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "bin", "Scripts"))
        (destination / "VIBE-VERSION").write_text(VIBE_VERSION + "\n", encoding="utf8")
    config = {"schemaVersion": 1, "profileId": profile_id, "environment": settings, "platform": target(), "pythonVersion": "3.12"}
    data = json.dumps(config, ensure_ascii=False, indent=2).encode("utf8")
    (destination / "deployment.json").write_bytes(data)
    (destination / "deployment.json.sha256").write_text(hashlib.sha256(data).hexdigest(), encoding="utf8")
    inventory(destination)


def target():
    """Pin the VSIX target to the system and architecture of its native Vibe dependencies. Exigences : DEVAI-006, DEVAI-171."""
    machine = platform.machine().lower()
    architecture = {"amd64": "x64", "x86_64": "x64", "arm64": "arm64", "aarch64": "arm64"}.get(machine)
    if sys.platform not in {"win32", "linux"} or not architecture:
        raise ValueError("Unsupported VSIX runtime target")
    return sys.platform + "-" + architecture


def inventory(destination):
    """Recalculate the complete runtime inventory after administrator-owned companion files are copied. Exigences : DEVAI-034, DEVAI-171."""
    destination = extended(destination)
    entries = {file.relative_to(destination).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest() for file in sorted(destination.rglob("*")) if file.is_file() and file.name != "INTEGRITY.json"}
    (destination / "INTEGRITY.json").write_text(json.dumps(entries, indent=2), encoding="utf8")


def extended(path):
    """Support long official SDK filenames on Windows without changing installed relative package paths. Exigences : DEVAI-006, DEVAI-171."""
    value = str(path.resolve())
    if os.name == "nt" and not value.startswith("\\\\?\\"):
        value = "\\\\?\\UNC\\" + value[2:] if value.startswith("\\\\") else "\\\\?\\" + value
    return Path(value)
