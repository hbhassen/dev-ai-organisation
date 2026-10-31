"""Execute fixed local scanner workflows without commits or exposing service credentials. Exigences : DEVAI-172, DEVAI-174, DEVAI-175, DEVAI-176."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

EXCLUDED = {".git", "node_modules", "target", "dist", "build", "bin", "obj", ".venv", "venv", ".scannerwork", ".sonarqube", ".ods-local", ".artifacts", "suiteInstall", "__pycache__"}


def clean_environment():
    """Pass machine tooling essentials without inheriting gateway or unrelated service credentials. Exigences : DEVAI-176."""
    allowed = {"PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "HOME", "USERPROFILE", "LANG", "JAVA_HOME", "DOTNET_ROOT", "APPDATA", "LOCALAPPDATA", "PROGRAMFILES", "PROGRAMFILES(X86)"}
    return {key: value for key, value in os.environ.items() if key.upper() in allowed}


def run(arguments, root, extra=None):
    """Execute an explicit scanner command without shell interpolation or forwarding its potentially sensitive logs. Exigences : DEVAI-172, DEVAI-174, DEVAI-176."""
    environment = clean_environment() | (extra or {})
    executable = shutil.which(arguments[0], path=environment.get("PATH", environment.get("Path")))
    if not executable:
        raise ValueError("Scanner prerequisite absent: " + arguments[0])
    child = subprocess.Popen([executable, *map(str, arguments[1:])], cwd=root, env=environment, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=os.name != "nt")
    try:
        status = child.wait(timeout=900)
    except BaseException:
        if os.name == "nt":
            subprocess.run(["taskkill.exe", "/PID", str(child.pid), "/T", "/F"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10, check=False)
        else:
            try:
                os.killpg(child.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        child.kill()
        child.wait(timeout=10)
        raise
    if status:
        raise ValueError("Scanner failed: " + arguments[0] + " (" + str(status) + ")")


def fingerprint(root):
    """Hash current tracked and untracked source bytes, rejecting symlinks and oversized snapshots. Exigences : DEVAI-172, DEVAI-175."""
    listing = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root, env=clean_environment(), capture_output=True, check=True, timeout=30).stdout
    names = sorted(set(listing.decode("utf8").split("\0")) - {""})
    if len(names) > 100000:
        raise ValueError("Workspace too large to fingerprint")
    digest = hashlib.sha256()
    total = 0
    for name in names:
        relative = Path(name)
        if any(part in EXCLUDED for part in relative.parts) or relative.name.startswith(".env") or relative.suffix in {".key", ".pem"} or relative.name == "credentials.json":
            continue
        path = root / relative
        if relative.is_absolute() or ".." in relative.parts or path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError("Unsafe workspace entry")
        digest.update(name.encode("utf8") + b"\0")
        if not path.exists():
            digest.update(b"DELETED\0")
            continue
        if not path.is_file():
            raise ValueError("Nested repository or special entry requires a separate workspace")
        total += path.stat().st_size
        if total > 512 * 1024 * 1024:
            raise ValueError("Workspace exceeds snapshot limit")
        with path.open("rb") as stream:
            digest.update(hashlib.file_digest(stream, "sha256").digest())
    return digest.hexdigest()


def dotnet_target(root):
    """Require one explicit solution or project rather than silently scanning a different module. Exigences : DEVAI-172, DEVAI-174."""
    targets = sorted(root.glob("*.sln")) + sorted(root.glob("*.slnx"))
    if not targets:
        targets = sorted(root.glob("*.csproj"))
    if len(targets) != 1:
        raise ValueError("Open a folder containing exactly one .NET solution or project")
    return targets[0].name


def validation_commands(root, stack):
    """Select existing stack workflows without accepting executable text from the model. Exigences : DEVAI-073, DEVAI-090, DEVAI-098."""
    if stack == "JAVA" and (root / "pom.xml").is_file():
        return [["mvn", "-B", "-ntp", "clean", "verify", "-DskipTests=false", "-Dmaven.test.skip=false", "-DskipITs=false"]]
    if stack == "ANGULAR" and (root / "package.json").is_file():
        scripts = json.loads((root / "package.json").read_text(encoding="utf-8-sig")).get("scripts", {})
        if not all(name in scripts for name in ("build", "test")):
            raise ValueError("package.json must declare build and test scripts")
        return [["npm", "run", "build"], ["npm", "test", "--", "--watch=false"]]
    if stack == "PYTHON" and any((root / name).is_file() for name in ("pyproject.toml", "requirements.txt", "setup.py")):
        executable = next((str(p) for p in (root / ".venv/Scripts/python.exe", root / ".venv/bin/python") if p.is_file()), sys.executable)
        return [[executable, "-m", "compileall", "-q", "-x", r"(^|[/\\])(?:\.venv|venv|node_modules|\.git)([/\\]|$)", "."], [executable, "-m", "pytest", "-q", "--junitxml=.ods-local/validation-tests.xml"]]
    if stack == "DOTNET":
        target = dotnet_target(root)
        return [["dotnet", "build", target, "--no-incremental"], ["dotnet", "test", target, "--no-build", "--logger", "trx", "--results-directory", ".ods-local/test-results"]]
    raise ValueError("No supported build/test workflow at the workspace root")


def validate(root, stack, output):
    """Run real compilation/tests and return bounded diagnostics for automatic repair, never a fabricated success. Exigences : DEVAI-073, DEVAI-090, DEVAI-098, DEVAI-176."""
    results = []
    try:
        commands = validation_commands(root, stack)
    except (ValueError, OSError) as error:
        return {"passed": False, "commands": [], "log": str(error)}
    for index, command in enumerate(commands):
        environment = clean_environment() | {"CI": "true"}
        executable = shutil.which(command[0], path=environment.get("PATH", environment.get("Path")))
        if not executable:
            return {"passed": False, "commands": results, "log": "Missing prerequisite: " + Path(command[0]).name}
        log = output / (str(index) + ".log")
        with log.open("wb") as stream:
            child = subprocess.Popen([executable, *command[1:]], cwd=root, env=environment, stdin=subprocess.DEVNULL, stdout=stream, stderr=subprocess.STDOUT, start_new_session=os.name != "nt")
            try:
                code = child.wait(timeout=900)
            except subprocess.TimeoutExpired:
                if os.name == "nt":
                    subprocess.run(["taskkill.exe", "/PID", str(child.pid), "/T", "/F"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10, check=False)
                else:
                    os.killpg(child.pid, signal.SIGKILL)
                child.kill()
                child.wait(timeout=10)
                code = -1
        with log.open("rb") as stream:
            stream.seek(max(0, log.stat().st_size - 18000))
            text = stream.read().decode("utf8", errors="replace")
        for path, placeholder in ((root, "<WORKSPACE>"), (Path.home(), "<USER_HOME>")):
            for value in (str(path), path.as_posix()):
                for repetitions in (8, 4, 2, 1):
                    text = text.replace(value.replace("\\", "\\" * repetitions), placeholder)
        text = re.sub(r"[A-Za-z]:[\\/]+Users[\\/]+[^\\/\s]+", "<USER_HOME>", text, flags=re.IGNORECASE)
        results.append({"arguments": [Path(command[0]).name, *command[1:]], "exitCode": code, "log": text})
        if code:
            return {"passed": False, "commands": results, "log": text}
    count = 0
    if stack == "JAVA":
        for directory in ("surefire-reports", "failsafe-reports"):
            for file in root.glob("**/target/" + directory + "/TEST-*.xml"):
                if file.is_symlink() or not file.resolve().is_relative_to(root):
                    raise ValueError("Unsafe test report")
                try:
                    report = ET.parse(file).getroot()
                except ET.ParseError:
                    return {"passed": False, "commands": results, "log": "Invalid test XML report"}
                if int(report.get("failures", 0)) or int(report.get("errors", 0)):
                    return {"passed": False, "commands": results, "log": "Failing tests in XML report"}
                count += int(report.get("tests", 0)) - int(report.get("skipped", 0))
        if not count:
            return {"passed": False, "commands": results, "tests": 0, "log": "No executed Maven tests: add tests; skipping tests cannot validate a correction"}
    return {"passed": True, "commands": results, "tests": count if stack == "JAVA" else None, "log": results[-1]["log"]}


def sbom(root, stack, output):
    """Run official CycloneDX generators for the selected stack and validate the resulting document. Exigences : DEVAI-172."""
    destination = output / "bom.json"
    if stack == "JAVA":
        if not (root / "pom.xml").is_file():
            raise ValueError("Maven pom.xml required")
        run(["mvn", "-B", "-ntp", "org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom", "-DschemaVersion=1.6", "-DoutputFormat=json", "-DoutputName=bom", "-DoutputDirectory=" + str(output)], root)
    elif stack == "ANGULAR":
        if not (root / "package-lock.json").is_file():
            raise ValueError("npm package-lock.json required for resolved dependency versions")
        run(["npx", "--yes", "@cyclonedx/cyclonedx-npm@6.0.1", "package.json", "--package-lock-only", "--spec-version", "1.6", "--output-format", "JSON", "--output-file", destination], root)
    elif stack == "PYTHON":
        command = ["cyclonedx-py"]
        if (root / "poetry.lock").is_file():
            command += ["poetry"]
        elif (root / "Pipfile.lock").is_file():
            command += ["pipenv"]
        elif (root / "requirements.txt").is_file():
            command += ["requirements", "requirements.txt"]
        else:
            raise ValueError("Python needs pinned requirements.txt, poetry.lock or Pipfile.lock")
        run([*command, "--spec-version", "1.6", "--output-format", "JSON", "--output-file", destination], root)
    elif stack == "DOTNET":
        run(["dotnet", "CycloneDX", dotnet_target(root), "--output", output, "--filename", "bom.json", "--output-format", "Json", "--spec-version", "1.6", "--exclude-dev", "--exclude-test-projects"], root)
    else:
        raise ValueError("Unknown development stack")
    if not destination.is_file() or destination.stat().st_size > 6_000_000:
        raise ValueError("Missing or oversized CycloneDX output")
    result = json.loads(destination.read_text(encoding="utf-8-sig"))
    if result.get("bomFormat") != "CycloneDX" or not isinstance(result.get("components"), list):
        raise ValueError("Invalid CycloneDX document")
    return {"sbom": result}


def sonar(root, stack, output, configuration):
    """Submit local sources using the actual stack scanner and return its own Compute Engine task identity. Exigences : DEVAI-174, DEVAI-176."""
    metadata = output / "report-task.txt"
    common = {"sonar.host.url": configuration["url"], "sonar.projectKey": configuration["projectKey"], "sonar.scanner.metadataFilePath": str(metadata)}
    credentials = {"SONAR_TOKEN": configuration["token"], "SONAR_HOST_URL": configuration["url"]}
    arguments = ["-D" + key + "=" + value for key, value in common.items()]
    if stack == "JAVA":
        run(["mvn", "-B", "-ntp", "verify"], root)
        run(["mvn", "-B", "-ntp", "org.sonarsource.scanner.maven:sonar-maven-plugin:5.0.0.4389:sonar", *arguments], root, credentials)
    elif stack in {"ANGULAR", "PYTHON"}:
        run(["sonar-scanner", *arguments, "-Dsonar.sources=."], root, credentials)
    elif stack == "DOTNET":
        # Scanner .NET reads this documented source-level environment provider in both phases.
        credentials = {"SONARQUBE_SCANNER_PARAMS": json.dumps(common | {"sonar.token": configuration["token"]})}
        run(["dotnet", "sonarscanner", "begin", "/k:" + configuration["projectKey"]], root, credentials)
        try:
            run(["dotnet", "build", dotnet_target(root), "--no-incremental"], root)
        finally:
            run(["dotnet", "sonarscanner", "end"], root, credentials)
    else:
        raise ValueError("Unknown development stack")
    if not metadata.is_file() or metadata.stat().st_size > 65536:
        raise ValueError("Scanner did not produce its task metadata")
    properties = dict(line.split("=", 1) for line in metadata.read_text(encoding="utf8").splitlines() if "=" in line)
    task = properties.get("ceTaskId", "")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", task) or properties.get("projectKey") != configuration["projectKey"]:
        raise ValueError("Scanner returned inconsistent task metadata")
    return {"ceTaskId": task}


def execute(request):
    """Bind the scanner result to a stable saved-source fingerprint and an owned temporary output directory. Exigences : DEVAI-172, DEVAI-174, DEVAI-175."""
    root = Path(request["root"]).resolve(strict=True)
    before = fingerprint(root)
    if request["operation"] == "fingerprint":
        return {"fingerprint": before}
    with tempfile.TemporaryDirectory(prefix="ods-analysis-") as directory:
        if request["operation"] == "sbom":
            result = sbom(root, request["stack"], Path(directory))
        elif request["operation"] == "validate":
            result = validate(root, request["stack"], Path(directory))
        elif request["operation"] == "sonar":
            result = sonar(root, request["stack"], Path(directory), request["sonar"])
        else:
            raise ValueError("Unknown local analysis operation")
    if before != fingerprint(root):
        raise ValueError("Sources changed during analysis; save and retry")
    return result | {"fingerprint": before, "profile": request["stack"], "freshness": "LOCAL_SAVED_SOURCES"}


def main():
    """Accept one private bounded request and keep raw process output and credentials out of diagnostics. Exigences : DEVAI-176."""
    try:
        payload = sys.stdin.buffer.read(8_388_609)
        if len(payload) > 8_388_608:
            raise ValueError("Request too large")
        result = execute(json.loads(payload))
        sys.stdout.write(json.dumps(result, ensure_ascii=True))
        return 0
    except (ValueError, OSError, subprocess.SubprocessError, KeyError):
        sys.stderr.write("Local analysis failed: verify scanner prerequisites, saved sources and managed configuration.\n")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
