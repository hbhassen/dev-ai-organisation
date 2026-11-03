"""Tests hors réseau du schéma, des dates et des primitives de signature. MECA-007."""
import base64
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import tempfile
import unittest

from meca.cli import generate_keys
from meca.policy import MAX_LONG, MecaError, date, decode, encode, load_keys, renew, sign, validate, verify


def fixture(now=None):
    """Fournit une politique synthétique cohérente avec le contrat 0.6.0. MECA-007."""
    now = now or datetime.now(timezone.utc).replace(microsecond=0)
    return {"schemaVersion": 1, "policyVersion": 7, "issuedAt": (now - timedelta(hours=1)).isoformat(), "expiresAt": (now + timedelta(hours=1)).isoformat(), "gitlabInstance": "https://gitlab.invalid", "defaultEffect": "DENY", "allowedProviders": ["MISTRAL"], "models": [{"alias": "coding", "provider": "MISTRAL", "model": "test-model", "tools": True}], "projects": [{"projectId": 42, "aiEnabled": True, "allowedActions": ["CHAT", "AGENT_RUN"], "modelAlias": "coding", "monthlyTokenQuota": 10000, "monthlyUserTokenQuota": 5000, "allowedAgents": ["implement-requirement"], "contextMode": "LOCAL_GOVERNED", "allowFreeText": True, "sonarProjectKey": "service-one"}]}


class PolicyTests(unittest.TestCase):
    """Empêche de signer des politiques ambiguës, incompatibles ou dépassant la durée autorisée. MECA-007."""

    def test_renew_is_once_exactly_24_hours_and_preserves_rules(self):
        """Le renouvellement ne modifie ni les droits ni l'objet original. MECA-003, MECA-007."""
        now = datetime(2030, 1, 2, 3, 4, 5, tzinfo=timezone.utc)
        original = fixture(now)
        result = renew(original, now)
        self.assertEqual(8, result["policyVersion"])
        self.assertEqual(7, original["policyVersion"])
        self.assertEqual(original["projects"], result["projects"])
        self.assertEqual("2030-01-02T03:04:05Z", result["issuedAt"])
        self.assertEqual(timedelta(hours=24), date(result["expiresAt"]) - date(result["issuedAt"]))
        validate(result, "https://gitlab.invalid", now=now)

    def test_expired_policy_can_be_renewed_but_not_published(self):
        """Autorise uniquement la lecture d'une politique expirée avant renouvellement. MECA-003, MECA-007."""
        p = fixture(datetime.now(timezone.utc) - timedelta(days=3))
        validate(p, "https://gitlab.invalid", allow_expired=True)
        with self.assertRaises(MecaError):
            validate(p, "https://gitlab.invalid")
        validate(renew(p), "https://gitlab.invalid")

    def test_invalid_dates_versions_and_instance(self):
        """Refuse les dates futures, durées excessives, versions invalides et autre instance. MECA-002, MECA-007."""
        original = fixture()
        for field, value in [("policyVersion", True), ("policyVersion", 0), ("policyVersion", MAX_LONG + 1), ("schemaVersion", True), ("issuedAt", "2026-01-01"), ("issuedAt", "2026-01-01T00:00:00"), ("expiresAt", "2100-01-01T00:00:00Z"), ("gitlabInstance", "https://other.invalid")]:
            with self.subTest(field=field, value=value):
                p = deepcopy(original)
                p[field] = value
                with self.assertRaises(MecaError):
                    validate(p, "https://gitlab.invalid")
        p = fixture(datetime.now(timezone.utc) + timedelta(days=2))
        with self.assertRaises(MecaError):
            validate(p, "https://gitlab.invalid", allow_expired=True)
        p["policyVersion"] = MAX_LONG
        with self.assertRaises(MecaError):
            renew(p)

    def test_project_and_catalog_boundaries(self):
        """Refuse les types, références, quotas et champs inconnus sans perdre d'informations. MECA-002, MECA-007."""
        mutations = [("projectId", True), ("projectId", 0), ("monthlyTokenQuota", -1), ("monthlyUserTokenQuota", 1.2), ("allowFreeText", "true"), ("allowedAgents", "agent"), ("modelAlias", []), ("modelAlias", "unknown"), ("allowedActions", ["UNKNOWN"]), ("aiEnabled", False), ("sonarProjectKey", ""), ("sonarProjectKey", "123"), ("sonarProjectKey", "../escape"), ("sonarProjectKey", "a" * 401), ("sonarProjectKey", None), ("unknown", True)]
        for field, value in mutations:
            with self.subTest(field=field, value=value):
                p = fixture()
                p["projects"][0][field] = value
                with self.assertRaises(MecaError):
                    validate(p, "https://gitlab.invalid")
        p = fixture()
        p["projects"].append(deepcopy(p["projects"][0]))
        with self.assertRaises(MecaError):
            validate(p, "https://gitlab.invalid")
        p = fixture()
        p["models"].append(deepcopy(p["models"][0]))
        with self.assertRaises(MecaError):
            validate(p, "https://gitlab.invalid")
        p = fixture()
        p["projects"] = [{"projectId": 1, "aiEnabled": False, "allowedActions": [], "monthlyTokenQuota": 0, "sonarProjectKey": "sonar:one"}]
        validate(p, "https://gitlab.invalid")

    def test_json_duplicates_size_and_utf8(self):
        """Vérifie les accents et refuse JSON ambigu ou trop volumineux. MECA-002, MECA-007."""
        for data in [b'{"x":1,"x":2}', b'\xff', b' ' * 1_048_577]:
            with self.assertRaises(MecaError):
                decode(data)
        p = fixture()
        p["models"][0]["alias"] = "modèle"
        p["projects"][0]["modelAlias"] = "modèle"
        self.assertEqual(p, decode(encode(p)))

    def test_real_signature_key_formats_and_tampering(self):
        """Teste Ed25519 réel, formats DER attendus par Java, clés distinctes et octets modifiés. MECA-003, MECA-007."""
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            generate_keys(root / "one")
            generate_keys(root / "two")
            private, public = load_keys(root / "one/signing.key", root / "one/public-key.txt")
            data = encode(fixture())
            signature = sign(data, private)
            self.assertEqual(64, len(base64.b64decode(signature)))
            self.assertEqual(bytes.fromhex("302a300506032b6570032100"), base64.b64decode((root / "one/public-key.txt").read_bytes())[:12])
            self.assertEqual(bytes.fromhex("302e020100300506032b657004220420"), base64.b64decode((root / "one/signing.key").read_bytes())[:16])
            verify(data, signature, public)
            for invalid in (data + b"\n", data.replace(b"\n", b"\r\n")):
                with self.assertRaises(MecaError):
                    verify(invalid, signature, public)
            with self.assertRaises(MecaError):
                load_keys(root / "one/signing.key", root / "two/public-key.txt")
            with self.assertRaises(MecaError):
                generate_keys(root / "one")


if __name__ == "__main__":
    unittest.main()
