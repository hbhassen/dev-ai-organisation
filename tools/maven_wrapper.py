"""Bootstrap the configured Maven distribution without PowerShell. Exigences : DEVAI-005, DEVAI-144."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile


def extract_verified(archive, destination, checksum):
    """Check the pinned archive and reject escaping ZIP paths before extraction. Exigences : DEVAI-035, DEVAI-144."""
    if not re.fullmatch(r"[a-fA-F0-9]{64}", checksum) or hashlib.sha256(archive.read_bytes()).hexdigest() != checksum.lower():
        raise ValueError("Maven distribution SHA-256 mismatch")
    destination = destination.resolve()
    with zipfile.ZipFile(archive) as bundle:
        for entry in bundle.infolist():
            target = (destination / entry.filename.replace("\\", "/")).resolve()
            if not target.is_relative_to(destination) or re.match(r"^[A-Za-z]:", entry.filename):
                raise ValueError("Unsafe Maven archive path")
        bundle.extractall(destination)


def maven(root):
    """Resolve the pinned Maven cache or download and verify it using wrapper properties. Exigences : DEVAI-005, DEVAI-144."""
    properties = dict(line.strip().split("=", 1) for line in (root / ".mvn/wrapper/maven-wrapper.properties").read_text().splitlines() if "=" in line and not line.lstrip().startswith("#"))
    url = properties["distributionUrl"]
    if os.environ.get("MVNW_REPOURL"):
        url = os.environ["MVNW_REPOURL"].rstrip("/") + "/org/apache/maven/" + url.split("/org/apache/maven/", 1)[1]
    checksum = properties["distributionSha256Sum"].lower()
    if not re.fullmatch(r"[a-f0-9]{64}", checksum):
        raise ValueError("A pinned Maven distribution SHA-256 is required")
    name = url.rsplit("/", 1)[-1]
    if not re.fullmatch(r"apache-maven-[0-9.]+-bin.zip", name):
        raise ValueError("Expected an Apache Maven binary ZIP distribution")
    cache = root / ".artifacts/maven" / checksum
    if not cache.resolve().is_relative_to(root.resolve()):
        raise ValueError("Maven cache must stay inside the project")
    executable = cache / name.removesuffix("-bin.zip") / "bin" / ("mvn.cmd" if os.name == "nt" else "mvn")
    if not executable.is_file():
        cache.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="download-", dir=cache.parent) as temporary:
            temporary = Path(temporary)
            archive = temporary / name
            request = urllib.request.Request(url)
            if os.environ.get("MVNW_USERNAME") and os.environ.get("MVNW_PASSWORD"):
                import base64
                credentials = (os.environ["MVNW_USERNAME"] + ":" + os.environ["MVNW_PASSWORD"]).encode()
                request.add_header("Authorization", "Basic " + base64.b64encode(credentials).decode())
            with urllib.request.urlopen(request, timeout=60) as response, archive.open("wb") as output:
                shutil.copyfileobj(response, output)
            extracted = temporary / "extracted"
            extract_verified(archive, extracted, checksum)
            if not (extracted / executable.relative_to(cache)).is_file():
                raise ValueError("Maven executable missing from distribution")
            extracted.rename(cache)
    return [str(executable)] if os.name == "nt" else ["bash", str(executable)]


def main():
    """Forward Maven arguments and its exact exit status without invoking a PowerShell wrapper. Exigences : DEVAI-005, DEVAI-140."""
    root = Path(__file__).resolve().parents[1]
    try:
        return subprocess.run(maven(root) + sys.argv[1:]).returncode
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(f"Maven bootstrap failed: {type(error).__name__}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
