"""Build, test and package ODS using Python's standard library, without PowerShell. Exigences : DEVAI-005, DEVAI-138, DEVAI-144, DEVAI-145."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile

import organisation_profile

ROOT = Path(__file__).resolve().parents[1]


def run(arguments, cwd=ROOT, capture=False, env=None):
    """Execute checked argument vectors and stop on any failed build or test command. Exigences : DEVAI-140, DEVAI-144."""
    result = subprocess.run([str(argument) for argument in arguments], cwd=cwd, env=env, check=True, text=True, stdout=subprocess.PIPE if capture else None, stderr=subprocess.STDOUT if capture else None)
    return result.stdout.strip() if capture else None


def identity(root):
    """Capture the exact Git commit and dirty state without recording workstation identity. Exigences : DEVAI-144, DEVAI-145."""
    return {"sourceCommit": run(["git", "rev-parse", "HEAD"], cwd=root, capture=True), "sourceDirty": bool(run(["git", "status", "--porcelain", "--untracked-files=normal"], cwd=root, capture=True))}


def delivery_identity(before, after):
    """Reject a changed source commit and retain dirty state from either end of the build. Exigences : DEVAI-144, DEVAI-145."""
    if before["sourceCommit"] != after["sourceCommit"]:
        raise ValueError("The source commit changed during build; rebuild before delivery")
    return {"sourceCommit": before["sourceCommit"], "sourceDirty": before["sourceDirty"] or after["sourceDirty"]}


def digest(path):
    """Hash the actual generated artifact for installed-engine and delivery verification. Exigences : DEVAI-141, DEVAI-145."""
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def test_delivery(java, engine, gateway, root=ROOT):
    """Start real JARs and require health plus rejection of anonymous business requests. Exigences : DEVAI-145."""
    run([java, "-jar", engine, "--version"], cwd=root)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    smoke = root / ".artifacts/smoke" / uuid.uuid4().hex
    smoke.mkdir(parents=True)
    arguments = [str(java), "-jar", str(gateway), f"--server.port={port}", "--server.address=127.0.0.1", "--ods.oidc-issuer=", "--spring.profiles.active=local", "--spring.datasource.url=jdbc:h2:mem:smoke;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"]
    with (smoke / "gateway.log").open("wb") as output, (smoke / "gateway-error.log").open("wb") as errors:
        process = subprocess.Popen(arguments, cwd=smoke, stdout=output, stderr=errors, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        try:
            ready = False
            for _ in range(60):
                if process.poll() is not None:
                    raise ValueError("Gateway exited before readiness; inspect ignored .artifacts/smoke logs")
                try:
                    with urllib.request.urlopen(f"http://127.0.0.1:{port}/health/live", timeout=2) as response:
                        ready = json.load(response)["status"] == "UP"
                    if ready:
                        break
                except (OSError, ValueError):
                    pass
                time.sleep(0.5)
            if not ready:
                raise ValueError("Gateway smoke test timed out")
            denied = 0
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/v1/capabilities?projectId=1", timeout=5) as response:
                    denied = response.status
            except urllib.error.HTTPError as error:
                denied = error.code
                error.close()
            if denied not in (401, 403):
                raise ValueError(f"Anonymous API denial failed: {denied}")
            print("Package smoke: engine version, gateway startup, health and anonymous API denial passed.", flush=True)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)


def preserve_suite(root):
    """Preserve every existing delivery file before replacement, including locked-directory fallback. Exigences : DEVAI-144, DEVAI-145."""
    root = root.resolve()
    suite = root / "suiteInstall"
    if suite.is_symlink() or suite.resolve() != suite:
        raise ValueError("Unexpected delivery directory")
    if suite.exists() and any(suite.iterdir()):
        previous = root / ".artifacts" / ("previous-suite-" + uuid.uuid4().hex)
        previous.parent.mkdir(parents=True, exist_ok=True)
        if previous.resolve().parent != (root / ".artifacts").resolve() or not previous.resolve().is_relative_to(root):
            raise ValueError("Unexpected delivery backup directory")
        for entry in suite.rglob("*"):
            if entry.is_symlink() or not entry.resolve().is_relative_to(suite):
                raise ValueError("Unexpected linked file in previous delivery")
        try:
            suite.rename(previous)
        except OSError:
            shutil.copytree(suite, previous)
            for entry in suite.rglob("*"):
                if entry.is_file():
                    entry.replace(previous / entry.relative_to(suite))
    suite.mkdir(exist_ok=True)
    return suite


def verify_vsix(vsix, version, engine):
    """Validate archive entries, manifest version and the exact bundled engine before delivery. Exigences : DEVAI-141, DEVAI-145."""
    with zipfile.ZipFile(vsix) as archive:
        for name in ("extension/package.json", "extension/dist/extension.js", "extension/engine/ods-java-core.jar", "extension/engine/ods-java-core.jar.sha256", "extension/media/view.js"):
            if name not in archive.namelist():
                raise ValueError(f"VSIX entry missing: {name}")
        if json.loads(archive.read("extension/package.json"))["version"] != version:
            raise ValueError("VSIX version mismatch")
        expected = digest(engine)
        if hashlib.sha256(archive.read("extension/engine/ods-java-core.jar")).hexdigest() != expected or archive.read("extension/engine/ods-java-core.jar.sha256").decode().strip() != expected:
            raise ValueError("VSIX engine digest mismatch")


def package(root, version, java, engine, gateway, vsix, profile, source):
    """Assemble the autonomous delivery, validate copied binaries and archive only this version. Exigences : DEVAI-144, DEVAI-145."""
    suite = preserve_suite(root)
    for part in ("bin", "config", "docs", "scripts", "examples"):
        (suite / part).mkdir(exist_ok=True)
    for artifact in (engine, gateway, vsix):
        shutil.copy2(artifact, suite / "bin")
    for config in (root / "config").glob("*.example"):
        shutil.copy2(config, suite / "config")
    if profile:
        organisation_profile.deliver(profile, suite / "config")
    for document in [*(root / "docs").glob("*.md"), *[root / name for name in ("README.md", "CHANGELOG.md", "specification.md", "prompt-developpement.md")]]:
        shutil.copy2(document, suite / "docs")
    for name in ("start_gateway.py", "start-gateway.ps1", "start-gateway.bat", "install-extension.bat", "prepare-prompt-bundle.ps1"):
        shutil.copy2(root / "scripts" / name, suite / "scripts")
    for example in (root / "examples").rglob("*"):
        if example.is_file() and example.suffix in (".json", ".yml", ".md", ".py") and "__pycache__" not in example.parts:
            target = suite / "examples" / example.relative_to(root / "examples")
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(example, target)
    (suite / "README.md").write_text(f"# ODS {version}\n\nStart with [installation](docs/INSTALLATION.md), [configuration](docs/CONFIGURATION.md), [validation](docs/VALIDATION.md) and [requirement coverage](docs/CONFORMITE.md).\n\nInstall the VSIX with scripts/install-extension.bat. Start the local gateway with scripts/start-gateway.bat (Java 21 and Python 3.11+ required). External services require organizational configuration.\n\nThis is a development delivery; consult BUILD.json for its exact source identity and SHA256SUMS for artifact digests.\n", encoding="utf8")
    (suite / "VERSION").write_text(version + "\n", encoding="utf8")
    build_identity = delivery_identity(source, identity(root)) | {"version": version, "builtAtUtc": datetime.now(timezone.utc).isoformat(), "profileId": json.loads(profile.read_text(encoding="utf8"))["id"] if profile else "default"}
    (suite / "BUILD.json").write_text(json.dumps(build_identity, indent=2), encoding="utf8")
    for document in suite.rglob("*"):
        if document.is_file() and document.suffix in (".md", ".py", ".ps1", ".bat", ".json", ".yml", ".example", ".env", ".properties"):
            if re.search(r"(?i)[A-Z]:\\Users\\|/home/[^<\s]+/|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", document.read_text(encoding="utf-8-sig")):
                raise ValueError(f"Sensitive workstation data in delivery: {document.name}")
    (suite / "SHA256SUMS").write_text("".join(f"{digest(artifact)}  bin/{artifact.name}\n" for artifact in sorted((suite / "bin").iterdir())), encoding="utf8")
    test_delivery(java, suite / "bin" / engine.name, suite / "bin" / gateway.name, root)
    archive = root / f"suiteInstall-{version}.zip"
    if archive.exists():
        shutil.copy2(archive, root / ".artifacts" / (archive.stem + "-previous-" + uuid.uuid4().hex + ".zip"))
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for entry in sorted(suite.rglob("*")):
            if entry.is_file():
                bundle.write(entry, entry.relative_to(root))
    print(f"Delivery verified: {archive.name}", flush=True)


def build(task, profile, root=ROOT):
    """Run the existing full validation sequence and its requested packaging stage. Exigences : DEVAI-005, DEVAI-138, DEVAI-144, DEVAI-145."""
    if profile:
        profile = (root / profile).resolve()
        organisation_profile.load(profile)
    java_name = shutil.which("java")
    if not java_name:
        raise ValueError("Java 21 is required on PATH")
    java = Path(java_name).resolve()
    jdk = java.parent.parent
    javac = java.with_name("javac.exe" if os.name == "nt" else "javac")
    if not javac.is_file() or not run([javac, "-version"], capture=True).startswith("javac 21."):
        raise ValueError("A complete JDK 21 must be on PATH")
    os.environ["JAVA_HOME"] = str(jdk)
    npm = shutil.which("npm.cmd" if os.name == "nt" else "npm")
    if not npm:
        raise ValueError("Node.js 24 with npm is required on PATH")
    version = ET.parse(root / "pom.xml").getroot().findtext("{http://maven.apache.org/POM/4.0.0}version")
    extension = root / "vscode-extension"
    manifest = json.loads((extension / "package.json").read_text(encoding="utf8"))
    if version != manifest["version"]:
        raise ValueError("Maven and VSIX versions differ")
    source = identity(root)
    (root / ".artifacts").mkdir(exist_ok=True)
    run([java, "tools/CheckDocumentation.java", root], cwd=root)
    run([sys.executable, "tools/check_python.py"], cwd=root)
    run([sys.executable, "-m", "unittest", "discover", "-s", "tools/tests", "-v"], cwd=root)
    run([sys.executable, "tools/maven_wrapper.py", "-B", "-ntp", "clean", "verify"], cwd=root)
    for arguments in (("ci", "--no-audit", "--no-fund"), ("run", "compile"), ("run", "lint"), ("test",), ("run", "browser:install"), ("run", "test:ui")):
        run([npm, *arguments], cwd=extension)
    engine = root / f"ods-java-core/target/ods-java-core-{version}-engine.jar"
    gateway = root / f"ods-gateway/target/ods-gateway-{version}.jar"
    for artifact in (engine, gateway):
        if not artifact.is_file() or artifact.stat().st_size < 10000:
            raise ValueError(f"Missing or empty artifact: {artifact.name}")
    test_delivery(java, engine, gateway, root)
    if task in ("Vsix", "Package"):
        (extension / "engine").mkdir(exist_ok=True)
        shutil.copy2(engine, extension / "engine/ods-java-core.jar")
        (extension / "engine/ods-java-core.jar.sha256").write_text(digest(engine), encoding="utf8")
        stage = root / ".artifacts" / ("vsix-stage-" + uuid.uuid4().hex)
        organisation_profile.stage(root, stage, profile)
        pack_manifest = json.loads((stage / "package.json").read_text(encoding="utf8"))
        vsix = root / ".artifacts" / f"{pack_manifest['name']}-{version}.vsix"
        run([extension / "node_modules/.bin" / ("vsce.cmd" if os.name == "nt" else "vsce"), "package", "--no-dependencies", "--allow-missing-repository", "--skip-license", "--out", vsix], cwd=stage)
        verify_vsix(vsix, version, engine)
        print(f"VSIX verified: .artifacts/{vsix.name}", flush=True)
        if task == "Package":
            package(root, version, java, engine, gateway, vsix, profile, source)
    print(f"SUCCESS: {task} {version}", flush=True)


def main():
    """Expose the shared batch entry point and propagate failures without publishing local diagnostics. Exigences : DEVAI-140, DEVAI-144."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task", choices=("Build", "Vsix", "Package"), default="Build")
    parser.add_argument("--organisation-profile", type=Path)
    arguments = parser.parse_args()
    try:
        build(arguments.task, arguments.organisation_profile)
        return 0
    except subprocess.CalledProcessError as error:
        print(f"Build command failed (exit {error.returncode}); inspect the preceding output.", file=sys.stderr)
        return error.returncode if 0 < error.returncode < 256 else 1
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(str(error) if isinstance(error, ValueError) else f"Build failed: {type(error).__name__}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
