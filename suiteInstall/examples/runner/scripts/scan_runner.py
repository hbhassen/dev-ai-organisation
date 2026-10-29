"""Trusted scanner-project entry point; never load a script from the target repository. Exigences : DEVAI-085, DEVAI-096, DEVAI-128."""
from __future__ import annotations
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """Keep archive credentials on the administratively selected GitLab origin. Exigences : DEVAI-036."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        """Refuse redirects before any authentication header could reach another origin. Exigences : DEVAI-036."""
        raise ValueError("Redirects are forbidden")


def required(name: str) -> str:
    """Read an administrative or pipeline value and reject unresolved placeholders. Exigences : DEVAI-127."""
    value = os.environ.get(name, "")
    if not value or "<" in value:
        raise ValueError(f"Configuration required: {name}")
    return value


def extract_source(data: bytes, destination: Path) -> None:
    """Extract a bounded GitLab archive without links, traversal or duplicate file targets. Exigences : DEVAI-054, DEVAI-085."""
    total = 0
    seen: set[str] = set()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in archive.infolist():
            path = PurePosixPath(entry.filename)
            if path.is_absolute() or ".." in path.parts or "\\" in entry.filename or ":" in entry.filename:
                raise ValueError("Unsafe archive path")
            if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError("Archive links are forbidden")
            if len(path.parts) < 2 or entry.is_dir():
                continue
            relative = Path(*path.parts[1:])
            if any(part.lower() in {".git", "credentials.json"} or part.lower().startswith(".env") for part in relative.parts):
                continue
            key = str(relative).lower()
            if key in seen:
                raise ValueError("Archive collision")
            seen.add(key)
            total += entry.file_size
            if total > 200_000_000 or len(seen) > 20000:
                raise ValueError("Archive expansion limit")
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("xb") as output:
                output.write(archive.read(entry))


def run(arguments: list[str], directory: Path, environment: dict[str, str], stdin: str | None = None) -> None:
    """Run an explicit command without a shell, preserving failures and the runner deadline. Exigences : DEVAI-073, DEVAI-085."""
    result = subprocess.run(arguments, cwd=directory, env=environment, input=stdin, text=True, timeout=600, check=False)
    if result.returncode:
        raise RuntimeError(f"Tool failed with exit code {result.returncode}: {arguments[0]}")


def fetch(work: Path) -> None:
    """Fetch exactly the authorized commit through GitLab's short-lived job token. Exigences : DEVAI-085, DEVAI-102."""
    project, commit = required("ODS_TARGET_PROJECT"), required("ODS_TARGET_COMMIT")
    if not re.fullmatch(r"[1-9][0-9]*", project) or not re.fullmatch(r"[a-f0-9]{40,64}", commit):
        raise ValueError("Invalid immutable target")
    if project not in json.loads(required("ODS_PROJECT_MAPPING")):
        raise ValueError("Target is absent from administrative runner mapping")
    base = required("CI_API_V4_URL")
    if not base.startswith("https://"):
        raise ValueError("GitLab HTTPS required")
    request = urllib.request.Request(f"{base}/projects/{project}/repository/archive.zip?sha={commit}", headers={"JOB-TOKEN": required("CI_JOB_TOKEN")})
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=90) as response:
        if response.geturl() != request.full_url:
            raise ValueError("Redirected source archive")
        data = response.read(50_000_001)
    if len(data) > 50_000_000:
        raise ValueError("Source archive too large")
    source = work / "source"
    source.mkdir(parents=True, exist_ok=False)
    extract_source(data, source)
    (work / "provenance.json").write_text(json.dumps({"projectId": project, "commit": commit, "profile": required("ODS_PROFILE"), "jobId": required("ODS_JOB_ID")}), encoding="utf-8")


def build(work: Path) -> None:
    """Validate target code in the unprivileged build job with a reduced child environment. Exigences : DEVAI-070, DEVAI-085, DEVAI-128."""
    environment = {key: value for key, value in os.environ.items() if key in {"PATH", "JAVA_HOME", "DOTNET_ROOT", "HOME", "LANG", "TMPDIR"}}
    profile = required("ODS_PROFILE")
    commands = {
        "JAVA": [["mvn", "-B", "-ntp", "clean", "verify"]],
        "ANGULAR": [["npm", "ci"], ["npm", "run", "build"], ["npm", "test", "--", "--watch=false"]],
        "PYTHON": [["python", "-m", "venv", ".venv"], [".venv/bin/python", "-m", "pip", "install", "-e", "."], [".venv/bin/python", "-m", "unittest", "discover", "-s", "tests"]],
        "DOTNET": [["dotnet", "restore"], ["dotnet", "build", "--no-restore"], ["dotnet", "test", "--no-build"]],
    }
    if profile not in commands:
        raise ValueError("Unsupported profile")
    for command in commands[profile]:
        run(command, work / "source", environment)
    binaries = work / "binaries"
    binaries.mkdir(exist_ok=True)
    if profile == "JAVA":
        for file in (work / "source").rglob("*.class"):
            if not file.is_symlink() and "target" in file.parts:
                target = binaries / file.relative_to(work / "source")
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, target)
    (work / "build-result.json").write_text(json.dumps({"status": "SUCCEEDED", "profile": profile}), encoding="utf-8")


def scan(work: Path) -> None:
    """Execute a genuine scanner in the credential-scoped scan job and preserve its provenance. Exigences : DEVAI-085, DEVAI-096, DEVAI-102."""
    environment = dict(os.environ)
    source, kind, profile = work / "source", required("ODS_SCAN_KIND"), required("ODS_PROFILE")
    if kind == "SONAR_SCAN":
        key = json.loads(required("ODS_PROJECT_MAPPING"))[required("ODS_TARGET_PROJECT")]["sonarKey"]
        if profile == "DOTNET":
            run(["dotnet", "sonarscanner", "begin", f"/k:{key}", f"/d:sonar.host.url={required('SONAR_HOST_URL')}", f"/d:sonar.token={required('SONAR_TOKEN')}", f"/d:sonar.scm.revision={required('ODS_TARGET_COMMIT')}"], source, environment)
            run(["dotnet", "build", "--no-incremental"], source, environment)
            run(["dotnet", "sonarscanner", "end", f"/d:sonar.token={required('SONAR_TOKEN')}"], source, environment)
        else:
            command = ["sonar-scanner", f"-Dsonar.projectKey={key}", "-Dsonar.sources=.", f"-Dsonar.scm.revision={required('ODS_TARGET_COMMIT')}", "-Dsonar.exclusions=**/node_modules/**,**/.venv/**,**/target/**", "-Dsonar.qualitygate.wait=true"]
            if profile == "JAVA":
                command.append("-Dsonar.java.binaries=../binaries/**/target/classes")
            run(command, source, environment)
        mode = "SCANNER"
    elif kind == "XRAY_SCAN":
        environment["JFROG_CLI_HOME_DIR"] = str((work / "jfrog-private").resolve())
        run(["jf", "config", "add", "ods", f"--url={required('JFROG_URL')}", "--access-token-stdin", "--interactive=false"], work, environment, required("JFROG_ACCESS_TOKEN"))
        with (work / "xray.json").open("w", encoding="utf-8") as output:
            completed = subprocess.run(["jf", "audit", "--sca", "--static-sca", "--format=json", "--licenses", "--server-id=ods"], cwd=source, env=environment, stdout=output, timeout=600, check=False)
        if completed.returncode:
            raise RuntimeError(f"Xray audit returned {completed.returncode}; consult the protected report")
        mode = "STATIC_SCA"
    else:
        raise ValueError("Unsupported scan kind")
    (work / "scan-result.json").write_text(json.dumps({"status": "SUCCEEDED", "kind": kind, "mode": mode, "commit": required("ODS_TARGET_COMMIT"), "profile": profile}), encoding="utf-8")


def main() -> None:
    """Dispatch only the three trusted phases and emit no credentials on failures. Exigences : DEVAI-085, DEVAI-132."""
    if len(sys.argv) != 2 or sys.argv[1] not in {"fetch", "build", "scan"}:
        raise SystemExit("Expected fetch, build or scan")
    work = Path("work")
    {"fetch": fetch, "build": build, "scan": scan}[sys.argv[1]](work)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Runner phase failed: {type(error).__name__}. Check prerequisites and protected job logs.", file=sys.stderr)
        raise SystemExit(1)
