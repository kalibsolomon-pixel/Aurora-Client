#!/usr/bin/env python3
"""Fail-closed Aurora release artifact checks used before and after publication."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path


REPO_ID = "kalibsolomon-pixel/Aurora-Client"
SCHEMA_VERSION = 1
VERSION_PATTERN = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?\Z")
SHA_PATTERN = re.compile(r"[0-9a-f]{40}\Z")
HASH_PATTERN = re.compile(r"[0-9a-f]{64}\Z")


class ReleaseError(ValueError):
    """An artifact or release cannot pass the publication gate."""


def require_version(version: str, channel: str) -> str:
    if not VERSION_PATTERN.fullmatch(version):
        raise ReleaseError("version must be X.Y.Z with an optional prerelease suffix")
    if channel == "stable" and "-" in version:
        raise ReleaseError("stable versions must not have a prerelease suffix")
    if channel == "beta" and not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+-beta\.[0-9]+", version):
        raise ReleaseError("beta versions must use X.Y.Z-beta.N")
    if channel == "nightly" and not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+-nightly\.[0-9]{8}", version):
        raise ReleaseError("nightly versions must use X.Y.Z-nightly.YYYYMMDD")
    if channel not in {"stable", "beta", "nightly"}:
        raise ReleaseError("channel must be stable, beta, or nightly")
    return "v" + version


def read_properties(path: Path) -> dict[str, str]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise ReleaseError(f"cannot read source version properties: {error}") from error
    values: dict[str, str] = {}
    for line in lines:
        line = line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if key in values:
            raise ReleaseError(f"duplicate Gradle property: {key}")
        values[key] = value
    return values


def find_runtime_jar(directory: Path, version: str) -> Path:
    jars = sorted(directory.glob("*.jar"))
    expected = directory / f"aurora-{version}.jar"
    if len(jars) != 1 or jars[0] != expected or not expected.is_file():
        raise ReleaseError(
            f"expected exactly one runtime JAR named {expected.name}; found {[p.name for p in jars]}"
        )
    return expected


def inspect_jar(path: Path, version: str, minecraft_version: str | None = None) -> dict[str, str]:
    try:
        with zipfile.ZipFile(path) as archive:
            entries = archive.infolist()
            if len(entries) > 10_000 or sum(entry.file_size for entry in entries) > 512 * 1024 * 1024:
                raise ReleaseError("runtime JAR exceeds archive inspection limits")
            metadata_entries = [entry for entry in entries if entry.filename == "fabric.mod.json"]
            if len(metadata_entries) != 1:
                raise ReleaseError("JAR must contain exactly one root fabric.mod.json")
            if metadata_entries[0].file_size > 256 * 1024:
                raise ReleaseError("fabric.mod.json exceeds its inspection limit")
            if archive.testzip() is not None:
                raise ReleaseError("JAR contains a corrupt archive entry")
            raw = archive.read("fabric.mod.json")
    except (OSError, zipfile.BadZipFile, RuntimeError) as error:
        raise ReleaseError(f"runtime JAR is not a valid readable archive: {error}") from error
    try:
        mod = json.loads(raw.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as error:
        raise ReleaseError("fabric.mod.json is malformed") from error
    if not isinstance(mod, dict) or mod.get("id") != "aurora":
        raise ReleaseError("runtime JAR mod id must be aurora")
    if mod.get("version") != version:
        raise ReleaseError("runtime JAR version does not match the requested version")
    if mod.get("environment") != "client":
        raise ReleaseError("runtime JAR must declare the client environment")
    depends = mod.get("depends")
    if not isinstance(depends, dict):
        raise ReleaseError("runtime JAR has no dependency map")
    for key in ("minecraft", "fabricloader", "java", "fabric-api"):
        if not isinstance(depends.get(key), str) or not depends[key].strip():
            raise ReleaseError(f"runtime JAR is missing the {key} dependency")
    if minecraft_version is not None and depends["minecraft"] != "~" + minecraft_version:
        raise ReleaseError("runtime JAR Minecraft requirement differs from gradle.properties")
    return {
        "modId": mod["id"],
        "minecraftRequirement": depends["minecraft"],
        "fabricLoaderRequirement": depends["fabricloader"],
        "fabricApiRequirement": depends["fabric-api"],
        "javaRequirement": depends["java"],
    }


def digest_and_size(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    if size == 0:
        raise ReleaseError("runtime JAR is empty")
    return digest.hexdigest(), size


def inspect_candidate(root: Path, jar_dir: Path, version: str, channel: str, source_sha: str) -> dict:
    tag = require_version(version, channel)
    if not SHA_PATTERN.fullmatch(source_sha):
        raise ReleaseError("source SHA must be a full lowercase commit SHA")
    properties = read_properties(root / "gradle.properties")
    if properties.get("mod_version") != version:
        raise ReleaseError("gradle.properties mod_version differs from the requested version")
    minecraft_version = properties.get("minecraft_version")
    if not minecraft_version:
        raise ReleaseError("gradle.properties is missing minecraft_version")
    for property_name in ("loader_version", "fabric_api_version"):
        if not properties.get(property_name):
            raise ReleaseError(f"gradle.properties is missing {property_name}")
    jar = find_runtime_jar(jar_dir, version)
    identity = inspect_jar(jar, version, minecraft_version)
    sha256, size = digest_and_size(jar)
    return {
        "schemaVersion": SCHEMA_VERSION,
        "version": version,
        "channel": channel,
        "tag": tag,
        "sourceSha": source_sha,
        "fileName": jar.name,
        "sizeBytes": size,
        "sha256": sha256,
        "minecraftVersion": minecraft_version,
        "buildFabricLoaderVersion": properties["loader_version"],
        "buildFabricApiVersion": properties["fabric_api_version"],
        **identity,
    }


def load_metadata(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ReleaseError("release metadata is unreadable or malformed") from error
    if (not isinstance(value, dict) or type(value.get("schemaVersion")) is not int
            or value["schemaVersion"] != SCHEMA_VERSION):
        raise ReleaseError("release metadata schema is unsupported")
    version, channel = value.get("version"), value.get("channel")
    if not isinstance(version, str) or not isinstance(channel, str):
        raise ReleaseError("release metadata lacks a version or channel")
    if value.get("tag") != require_version(version, channel):
        raise ReleaseError("release tag does not match the version")
    if value.get("fileName") != f"aurora-{version}.jar":
        raise ReleaseError("release asset filename does not match the version")
    if not isinstance(value.get("sourceSha"), str) or not SHA_PATTERN.fullmatch(value["sourceSha"]):
        raise ReleaseError("release metadata has an invalid source SHA")
    if not isinstance(value.get("sha256"), str) or not HASH_PATTERN.fullmatch(value["sha256"]):
        raise ReleaseError("release metadata has an invalid SHA-256")
    if type(value.get("sizeBytes")) is not int or value["sizeBytes"] <= 0:
        raise ReleaseError("release metadata has an invalid size")
    return value


def verify_bundle(metadata: dict, jar: Path, version: str, channel: str, source_sha: str) -> None:
    if (metadata["version"], metadata["channel"], metadata["sourceSha"]) != (
        version, channel, source_sha
    ):
        raise ReleaseError("handoff metadata does not match the requested release and source")
    if jar.name != metadata["fileName"]:
        raise ReleaseError("handoff JAR filename does not match the verified candidate")
    actual_hash, actual_size = digest_and_size(jar)
    if actual_hash != metadata["sha256"] or actual_size != metadata["sizeBytes"]:
        raise ReleaseError("handoff/public JAR SHA-256 or size differs from the verified candidate")
    identity = inspect_jar(jar, version, metadata["minecraftVersion"])
    if any(metadata.get(key) != value for key, value in identity.items()):
        raise ReleaseError("handoff/public JAR metadata differs from the verified candidate")


def github_json(repo: str, path: str, *, authenticated: bool, missing_ok: bool = False) -> dict | None:
    if repo != REPO_ID:
        raise ReleaseError("release tooling is pinned to the canonical Aurora-Client repository")
    request = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/{path}",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "aurora-release-verifier"},
    )
    if authenticated:
        token = os.environ.get("GH_TOKEN")
        if not token:
            raise ReleaseError("GH_TOKEN is required for release API checks")
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read(2 * 1024 * 1024 + 1)
    except urllib.error.HTTPError as error:
        if missing_ok and error.code == 404:
            return None
        raise ReleaseError(f"GitHub API returned HTTP {error.code} for {path}") from error
    except urllib.error.URLError as error:
        raise ReleaseError(f"GitHub API unavailable for {path}") from error
    if len(body) > 2 * 1024 * 1024:
        raise ReleaseError("GitHub API response is too large")
    try:
        value = json.loads(body)
    except json.JSONDecodeError as error:
        raise ReleaseError("GitHub API returned malformed JSON") from error
    if not isinstance(value, dict):
        raise ReleaseError("GitHub API returned an unexpected response")
    return value


def check_collision(repo: str, version: str, channel: str) -> None:
    tag = require_version(version, channel)
    encoded = urllib.parse.quote(tag, safe="")
    if github_json(repo, "git/ref/tags/" + encoded, authenticated=True, missing_ok=True) is not None:
        raise ReleaseError(f"tag {tag} already exists; published tags are never reused")
    if github_json(repo, "releases/tags/" + encoded, authenticated=True, missing_ok=True) is not None:
        raise ReleaseError(f"release {tag} already exists; published releases are never repaired automatically")


def validate_release_state(release: dict, metadata: dict, *, draft: bool) -> str:
    if release.get("tag_name") != metadata["tag"] or release.get("draft") is not draft:
        raise ReleaseError("release tag or draft state is wrong")
    if release.get("target_commitish") != metadata["sourceSha"]:
        raise ReleaseError("release target differs from the exact built source commit")
    if release.get("prerelease") is not (metadata["channel"] != "stable"):
        raise ReleaseError("release prerelease flag differs from the selected channel")
    if not draft and release.get("immutable") is not True:
        raise ReleaseError("published release is not immutable")
    assets = release.get("assets")
    if not isinstance(assets, list) or len(assets) != 1:
        raise ReleaseError("release must contain exactly one runtime JAR asset before finalization")
    asset = assets[0]
    if not isinstance(asset, dict) or asset.get("name") != metadata["fileName"]:
        raise ReleaseError("release runtime JAR asset name is wrong")
    if asset.get("size") != metadata["sizeBytes"] or asset.get("state") != "uploaded":
        raise ReleaseError("release runtime JAR asset is incomplete or has the wrong size")
    github_digest = asset.get("digest")
    if github_digest and github_digest != "sha256:" + metadata["sha256"]:
        raise ReleaseError("GitHub asset digest differs from the verified candidate")
    url = asset.get("browser_download_url")
    expected = f"https://github.com/{REPO_ID}/releases/download/{metadata['tag']}/{metadata['fileName']}"
    if url != expected:
        raise ReleaseError("release asset URL is not the expected immutable tag asset URL")
    return url


def download_public(url: str, path: Path, size: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "aurora-public-release-audit"})
    temporary = None
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            if response.status != 200:
                raise ReleaseError("public asset download did not return HTTP 200")
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix="aurora-public-", suffix=".part", delete=False) as output:
                temporary = Path(output.name)
                remaining = size
                while chunk := response.read(min(1024 * 1024, remaining + 1)):
                    output.write(chunk)
                    remaining -= len(chunk)
                    if remaining < 0:
                        raise ReleaseError("public asset exceeded its recorded size")
        if remaining != 0:
            raise ReleaseError("public asset was shorter than its recorded size")
        temporary.replace(path)
    except (urllib.error.HTTPError, urllib.error.URLError) as error:
        raise ReleaseError("anonymous public asset download failed") from error
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    inspect = sub.add_parser("inspect")
    inspect.add_argument("--source-root", type=Path, required=True)
    inspect.add_argument("--jar-dir", type=Path, required=True)
    inspect.add_argument("--version", required=True)
    inspect.add_argument("--channel", required=True)
    inspect.add_argument("--source-sha", required=True)
    inspect.add_argument("--output", type=Path, required=True)
    verify = sub.add_parser("verify")
    verify.add_argument("--metadata", type=Path, required=True)
    verify.add_argument("--jar-dir", type=Path, required=True)
    verify.add_argument("--version", required=True)
    verify.add_argument("--channel", required=True)
    verify.add_argument("--source-sha", required=True)
    collision = sub.add_parser("collision")
    collision.add_argument("--repo", required=True)
    collision.add_argument("--version", required=True)
    collision.add_argument("--channel", required=True)
    draft = sub.add_parser("draft")
    draft.add_argument("--repo", required=True)
    draft.add_argument("--metadata", type=Path, required=True)
    public = sub.add_parser("public")
    public.add_argument("--repo", required=True)
    public.add_argument("--metadata", type=Path, required=True)
    public.add_argument("--download-dir", type=Path, required=True)
    args = parser.parse_args()

    if args.command == "inspect":
        metadata = inspect_candidate(args.source_root, args.jar_dir, args.version, args.channel, args.source_sha)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(metadata, sort_keys=True))
    elif args.command == "verify":
        metadata = load_metadata(args.metadata)
        verify_bundle(metadata, args.jar_dir / metadata["fileName"], args.version, args.channel, args.source_sha)
        print(json.dumps(metadata, sort_keys=True))
    elif args.command == "collision":
        check_collision(args.repo, args.version, args.channel)
        print("requested tag and release are unused")
    elif args.command in {"draft", "public"}:
        metadata = load_metadata(args.metadata)
        release = github_json(
            args.repo, "releases/tags/" + urllib.parse.quote(metadata["tag"], safe=""),
            authenticated=args.command == "draft",
        )
        assert release is not None
        url = validate_release_state(release, metadata, draft=args.command == "draft")
        if args.command == "public":
            jar = args.download_dir / metadata["fileName"]
            download_public(url, jar, metadata["sizeBytes"])
            verify_bundle(metadata, jar, metadata["version"], metadata["channel"], metadata["sourceSha"])
            print(f"anonymous public JAR verified: {metadata['sizeBytes']} bytes, sha256:{metadata['sha256']}")
        else:
            print("draft contains the complete expected runtime JAR and exact source target")


if __name__ == "__main__":
    try:
        main()
    except (ReleaseError, OSError, KeyError, TypeError) as error:
        print(f"release verification failed: {error}", file=sys.stderr)
        sys.exit(1)
