"""Validate and stage organisation-specific builds without changing sources. Exigences : DEVAI-009, DEVAI-127, DEVAI-144."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
from urllib.parse import urlsplit


def fields(value, allowed, required=()):
    """Reject unknown configuration keys and missing mandatory fields early. Exigences : DEVAI-127."""
    if not isinstance(value, dict) or set(value) - set(allowed) or set(required) - set(value):
        raise ValueError("Invalid organisation profile fields")


def contained(root, name):
    """Resolve a declared relative input while rejecting links outside its profile root. Exigences : DEVAI-036, DEVAI-127."""
    if not isinstance(name, str) or not name or "\\" in name or ":" in name or PurePosixPath(name).is_absolute() or ".." in PurePosixPath(name).parts:
        raise ValueError("Profile paths must be relative and contained")
    path = (root / name).resolve()
    if not path.is_relative_to(root.resolve()) or path == root.resolve():
        raise ValueError("Profile path escapes its root")
    return path


def load(path):
    """Load a strict profile and environment files without evaluating code or exposing values in logs. Exigences : DEVAI-035, DEVAI-127."""
    profile = json.loads(path.read_text(encoding="utf-8-sig"))
    fields(profile, ("schemaVersion", "id", "extension", "gatewayUrl", "envFiles", "environment", "files"), ("schemaVersion", "id", "extension"))
    if profile["schemaVersion"] != 1 or not re.fullmatch(r"[a-z][a-z0-9-]{1,49}", profile["id"]):
        raise ValueError("Unsupported profile version or identifier")
    extension = profile["extension"]
    fields(extension, ("name", "publisher", "displayName"), ("name", "publisher", "displayName"))
    for key in ("name", "publisher"):
        if not isinstance(extension[key], str) or not re.fullmatch(r"[a-z][a-z0-9-]{1,69}", extension[key]):
            raise ValueError("Invalid extension identity")
    if not isinstance(extension["displayName"], str) or not 2 <= len(extension["displayName"]) <= 80 or any(ord(c) < 32 for c in extension["displayName"]):
        raise ValueError("Invalid extension display name")
    url = urlsplit(profile.get("gatewayUrl", "http://127.0.0.1:8080"))
    if not url.hostname or url.username or url.password or url.query or url.fragment or (url.scheme != "https" and not (url.scheme == "http" and url.hostname in ("127.0.0.1", "localhost", "::1"))):
        raise ValueError("Gateway HTTPS required outside loopback")
    environment = {}
    if not isinstance(profile.get("envFiles", []), list) or not isinstance(profile.get("files", []), list):
        raise ValueError("envFiles and files must be arrays")
    for name in profile.get("envFiles", []):
        for line in contained(path.parent, name).read_text(encoding="utf-8-sig").splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            if "=" not in line:
                raise ValueError("Invalid environment file line")
            key, value = line.split("=", 1)
            environment[key] = value
    if not isinstance(profile.get("environment", {}), dict):
        raise ValueError("environment must be an object")
    environment.update(profile.get("environment", {}))
    for key, value in environment.items():
        if not re.fullmatch(r"ODS_[A-Z0-9_]+|SPRING_PROFILES_ACTIVE", key) or not isinstance(value, str) or any(c in value for c in "\r\n\x00"):
            raise ValueError("Invalid environment key or value")
    targets = set()
    for item in profile.get("files", []):
        fields(item, ("source", "target"), ("source", "target"))
        source = contained(path.parent, item["source"])
        target = contained(Path("profile-config").resolve(), item["target"])
        if not source.is_file() or target.name in ("gateway.env", "organisation.json") or item["target"].lower() in targets:
            raise ValueError("Invalid or duplicate profile file")
        targets.add(item["target"].lower())
    return profile, environment


def stage(root, destination, profile_path=None):
    """Create a fresh VSIX staging tree containing public branding; managed runtime is assembled separately. Exigences : DEVAI-035, DEVAI-144."""
    destination.mkdir(parents=True, exist_ok=False)
    extension_root = root / "vscode-extension"
    for name in ("dist", "media", "engine"):
        shutil.copytree(extension_root / name, destination / name, ignore=shutil.ignore_patterns("*.map"))
    for name in ("README.md", "LICENSE"):
        source = extension_root / name
        if source.exists():
            shutil.copy2(source, destination / name)
    manifest = json.loads((extension_root / "package.json").read_text(encoding="utf8"))
    manifest.pop("scripts", None)
    manifest.pop("devDependencies", None)
    if profile_path:
        profile, _ = load(profile_path)
        manifest.update(profile["extension"])
        title = profile["extension"]["displayName"]
        manifest["contributes"]["viewsContainers"]["activitybar"][0]["title"] = title
        manifest["contributes"]["views"]["ods"][0]["name"] = title
        manifest["contributes"]["configuration"]["title"] = title
        for command in manifest["contributes"]["commands"]:
            command["title"] = command["title"].replace("ODS:", title + ":", 1)
    (destination / "package.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf8")


def deliver(profile_path, destination):
    """Deliver configuration examples without duplicating the intentional private-VSIX credentials. Exigences : DEVAI-127, DEVAI-145, DEVAI-167, DEVAI-176."""
    profile, environment = load(profile_path)
    destination.mkdir(parents=True, exist_ok=True)
    example = {key: (f"<{key}>" if re.search(r"TOKEN|PASSWORD|KEY|SECRET", key) and value else value) for key, value in environment.items()}
    (destination / "gateway.env").write_text("# Configuration example; managed credentials are embedded only in the private VSIX. DEVAI-167, DEVAI-176.\n" + "\n".join(f"{key}={value}" for key, value in sorted(example.items())) + "\n", encoding="utf8")
    for item in profile.get("files", []):
        target = contained(destination, item["target"])
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(contained(profile_path.parent, item["source"]), target)
    metadata = {"id": profile["id"], "extension": profile["extension"], "gatewayUrl": profile.get("gatewayUrl", "http://127.0.0.1:8080"), "profileSha256": hashlib.sha256(profile_path.read_bytes()).hexdigest(), "environmentKeys": sorted(environment)}
    (destination / "organisation.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf8")
    hashes = [f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.relative_to(destination).as_posix()}" for file in sorted(destination.rglob("*")) if file.is_file() and file.name != "CONFIG-SHA256SUMS"]
    (destination / "CONFIG-SHA256SUMS").write_text("\n".join(hashes) + "\n", encoding="utf8")


def main():
    """Expose validation, isolated staging and delivery actions to the Windows full-build script. Exigences : DEVAI-144."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "stage", "deliver"))
    parser.add_argument("--profile", type=Path)
    parser.add_argument("--destination", type=Path)
    args = parser.parse_args()
    if args.action == "validate":
        load(args.profile.resolve())
    elif args.action == "stage":
        stage(Path(__file__).resolve().parent.parent, args.destination, args.profile.resolve() if args.profile else None)
    else:
        deliver(args.profile.resolve(), args.destination)


if __name__ == "__main__":
    main()
