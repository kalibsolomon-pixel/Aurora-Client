import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from tools import verify_release_artifact as release


SHA = "a" * 40
VERSION = "2.1.1"
DEPENDS = {
    "minecraft": "~1.21.11",
    "fabricloader": ">=0.16.0",
    "java": ">=21",
    "fabric-api": "*",
}


class ReleaseArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jars = self.root / "build" / "libs"
        self.jars.mkdir(parents=True)
        (self.root / "gradle.properties").write_text(
            "mod_version=2.1.1\nminecraft_version=1.21.11\n"
            "loader_version=0.19.2\nfabric_api_version=0.141.4+1.21.11\n",
            encoding="utf-8",
        )
        self.jar = self.jars / "aurora-2.1.1.jar"
        self.write_jar()

    def write_jar(self, *, mod_id="aurora", version=VERSION, depends=None, metadata=True):
        mod = {
            "id": mod_id,
            "version": version,
            "environment": "client",
            "depends": DEPENDS if depends is None else depends,
        }
        with zipfile.ZipFile(self.jar, "w") as archive:
            if metadata:
                archive.writestr("fabric.mod.json", json.dumps(mod))
            archive.writestr("com/aurora/client/AuroraClient.class", b"synthetic fixture")

    def inspect(self):
        return release.inspect_candidate(self.root, self.jars, VERSION, "stable", SHA)

    def test_valid_candidate_and_handoff(self):
        metadata = self.inspect()
        self.assertEqual(metadata["tag"], "v2.1.1")
        self.assertEqual(metadata["minecraftRequirement"], "~1.21.11")
        release.verify_bundle(metadata, self.jar, VERSION, "stable", SHA)

    def test_wrong_mod_id(self):
        self.write_jar(mod_id="other")
        with self.assertRaisesRegex(release.ReleaseError, "mod id"):
            self.inspect()

    def test_wrong_version(self):
        self.write_jar(version="2.1.0")
        with self.assertRaisesRegex(release.ReleaseError, "version"):
            self.inspect()

    def test_missing_fabric_metadata(self):
        self.write_jar(metadata=False)
        with self.assertRaisesRegex(release.ReleaseError, "fabric.mod.json"):
            self.inspect()

    def test_malformed_fabric_metadata(self):
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("fabric.mod.json", b"{bad")
        with self.assertRaisesRegex(release.ReleaseError, "malformed"):
            self.inspect()

    def test_every_required_dependency_is_enforced(self):
        for dependency in DEPENDS:
            with self.subTest(dependency=dependency):
                self.write_jar(depends={key: value for key, value in DEPENDS.items() if key != dependency})
                with self.assertRaisesRegex(release.ReleaseError, dependency):
                    self.inspect()

    def test_minecraft_must_match_source_properties(self):
        self.write_jar(depends={**DEPENDS, "minecraft": "~1.21.10"})
        with self.assertRaisesRegex(release.ReleaseError, "Minecraft requirement"):
            self.inspect()

    def test_multiple_runtime_jars_are_ambiguous(self):
        (self.jars / "aurora-2.1.1-sources.jar").write_bytes(b"not a runtime jar")
        with self.assertRaisesRegex(release.ReleaseError, "exactly one"):
            self.inspect()

    def test_invalid_archive(self):
        self.jar.write_bytes(b"not a zip")
        with self.assertRaisesRegex(release.ReleaseError, "valid readable archive"):
            self.inspect()

    def test_handoff_hash_and_size_mismatch(self):
        metadata = self.inspect()
        self.jar.write_bytes(self.jar.read_bytes() + b"changed")
        with self.assertRaisesRegex(release.ReleaseError, "SHA-256 or size"):
            release.verify_bundle(metadata, self.jar, VERSION, "stable", SHA)

    def test_handoff_declared_size_alone_is_checked(self):
        metadata = self.inspect()
        metadata["sizeBytes"] += 1
        with self.assertRaisesRegex(release.ReleaseError, "SHA-256 or size"):
            release.verify_bundle(metadata, self.jar, VERSION, "stable", SHA)

    def test_source_version_must_match_request(self):
        (self.root / "gradle.properties").write_text("mod_version=2.1.0\nminecraft_version=1.21.11\n")
        with self.assertRaisesRegex(release.ReleaseError, "mod_version"):
            self.inspect()

    def test_channel_and_tag_cannot_silently_disagree(self):
        with self.assertRaisesRegex(release.ReleaseError, "stable"):
            release.require_version("2.1.1-beta.1", "stable")
        self.assertEqual(release.require_version("2.1.1-beta.1", "beta"), "v2.1.1-beta.1")

    def test_empty_or_wrong_asset_cannot_be_finalized(self):
        metadata = self.inspect()
        state = {
            "tag_name": "v2.1.1", "draft": True, "target_commitish": SHA,
            "prerelease": False, "assets": [],
        }
        with self.assertRaisesRegex(release.ReleaseError, "exactly one"):
            release.validate_release_state(state, metadata, draft=True)
        state["assets"] = [{"name": "source.zip", "size": metadata["sizeBytes"], "state": "uploaded"}]
        with self.assertRaisesRegex(release.ReleaseError, "asset name"):
            release.validate_release_state(state, metadata, draft=True)

    def test_existing_tag_or_release_is_a_hard_collision(self):
        with patch.object(release, "github_json", side_effect=[{"ref": "refs/tags/v2.1.1"}]):
            with self.assertRaisesRegex(release.ReleaseError, "already exists"):
                release.check_collision(release.REPO_ID, VERSION, "stable")
        with patch.object(release, "github_json", side_effect=[None, {"tag_name": "v2.1.1"}]):
            with self.assertRaisesRegex(release.ReleaseError, "already exists"):
                release.check_collision(release.REPO_ID, VERSION, "stable")

    def test_correct_draft_asset_and_public_immutability(self):
        metadata = self.inspect()
        url = f"https://github.com/{release.REPO_ID}/releases/download/v2.1.1/aurora-2.1.1.jar"
        state = {
            "tag_name": "v2.1.1", "draft": True, "target_commitish": SHA,
            "prerelease": False,
            "assets": [{
                "name": metadata["fileName"], "size": metadata["sizeBytes"],
                "state": "uploaded", "digest": "sha256:" + metadata["sha256"],
                "browser_download_url": url,
            }],
        }
        self.assertEqual(release.validate_release_state(state, metadata, draft=True), url)
        state["draft"] = False
        with self.assertRaisesRegex(release.ReleaseError, "not immutable"):
            release.validate_release_state(state, metadata, draft=False)
        state["immutable"] = True
        self.assertEqual(release.validate_release_state(state, metadata, draft=False), url)


if __name__ == "__main__":
    unittest.main()
