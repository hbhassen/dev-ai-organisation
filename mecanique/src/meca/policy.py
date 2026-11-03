"""Contrat de politique ODS 0.6.0 et signatures Ed25519 détachées. MECA-002, MECA-003."""
import base64
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

FILES = ("ai-project-policy.json", "ai-project-policy.sig")
MAX_SIZE = 1_048_576
MAX_LONG = 2**63 - 1
ACTIONS = {
    "CHAT": "discussion IA",
    "CODE_GENERATION": "génération de code",
    "SONAR_REMEDIATION": "correction Quality et couverture",
    "XRAY_REMEDIATION": "correction Security",
    "MR_DESCRIPTION": "pré-revue de merge request",
    "AGENT_RUN": "exécution de l'agent",
    "CHANGELOG": "génération du changelog",
    "RELEASE_NOTES": "génération des notes de version",
}


class MecaError(Exception):
    """Erreur affichable sans sortie brute de Git ni contenu de clé privée. MECA-006."""


def utcnow():
    """Utilise une horloge UTC commune aux validations et au renouvellement. MECA-003."""
    return datetime.now(timezone.utc).replace(microsecond=0)


def integer(value, minimum=0):
    """Refuse les booléens et les entiers incompatibles avec Java long. MECA-002."""
    return type(value) is int and minimum <= value <= MAX_LONG


def fields(value, allowed, required):
    """Refuse les champs inconnus au lieu de supprimer silencieusement des droits. MECA-002."""
    if not isinstance(value, dict) or set(value) - set(allowed) or set(required) - set(value):
        raise MecaError("Structure invalide : champs inconnus ou obligatoires manquants.")


def text(value):
    """Vérifie une chaîne non vide sans accepter les autres types JSON. MECA-002."""
    return isinstance(value, str) and bool(value.strip())


def sonar_key(value):
    """Reprend les limites des clés signées acceptées par ODS 0.6.0. MECA-002."""
    return isinstance(value, str) and bool(re.fullmatch(r"[A-Za-z0-9_.:-]{1,400}", value)) and not value.isdigit()


def date(value):
    """Accepte uniquement des dates ISO avec fuseau explicite comme le gateway. MECA-003."""
    try:
        if not isinstance(value, str) or "T" not in value:
            raise ValueError()
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if result.tzinfo is None:
            raise ValueError()
        return result
    except (TypeError, ValueError):
        raise MecaError("Date ISO invalide ; utiliser une date UTC terminée par Z.") from None


def validate(policy, instance, allow_expired=False, now=None):
    """Valide les champs connus, les références et les dates sans autoriser implicitement l'IA. MECA-002, MECA-003."""
    fields(policy, {"schemaVersion", "policyVersion", "issuedAt", "expiresAt", "gitlabInstance", "defaultEffect", "allowedProviders", "models", "projects"}, {"schemaVersion", "policyVersion", "issuedAt", "expiresAt", "gitlabInstance", "defaultEffect", "allowedProviders", "models", "projects"})
    if type(policy["schemaVersion"]) is not int or policy["schemaVersion"] != 1 or not integer(policy["policyVersion"], 1):
        raise MecaError("schemaVersion doit être 1 ; policyVersion doit être un entier positif.")
    if policy["gitlabInstance"] != instance or policy["defaultEffect"] != "DENY" or policy["allowedProviders"] != ["MISTRAL"]:
        raise MecaError("Instance GitLab, defaultEffect=DENY ou allowedProviders=[MISTRAL] incompatible.")
    current = now or utcnow()
    issued, expires = date(policy["issuedAt"]), date(policy["expiresAt"])
    if not issued < expires or expires - issued > timedelta(hours=24) or issued > current + timedelta(seconds=60):
        raise MecaError("Dates invalides : issuedAt <= maintenant+60s, durée positive de 24h maximum.")
    if not allow_expired and expires <= current:
        raise MecaError("Politique expirée : renouveler ses dates avant publication.")
    if not isinstance(policy["models"], list) or not policy["models"]:
        raise MecaError("Un catalogue de modèles non vide est requis.")
    aliases = set()
    for model in policy["models"]:
        fields(model, {"alias", "provider", "model", "tools"}, {"alias", "provider", "model", "tools"})
        if not text(model["alias"]) or model["alias"] in aliases or model["provider"] != "MISTRAL" or type(model["tools"]) is not bool or not isinstance(model["model"], str) or not re.fullmatch(r"[a-zA-Z0-9._:-]{1,128}", model["model"]):
            raise MecaError("Modèle invalide : alias unique, provider MISTRAL, identifiant valide et tools booléen requis.")
        aliases.add(model["alias"])
    if not isinstance(policy["projects"], list):
        raise MecaError("projects doit être une liste.")
    ids = set()
    for project in policy["projects"]:
        fields(project, {"projectId", "aiEnabled", "allowedActions", "modelAlias", "monthlyTokenQuota", "monthlyUserTokenQuota", "allowedAgents", "contextMode", "allowFreeText", "sonarProjectKey"}, {"projectId", "aiEnabled", "allowedActions", "monthlyTokenQuota"})
        if not integer(project["projectId"], 1) or project["projectId"] in ids:
            raise MecaError("projectId doit être un entier positif unique.")
        ids.add(project["projectId"])
        actions = project["allowedActions"]
        if type(project["aiEnabled"]) is not bool or not isinstance(actions, list) or any(not isinstance(a, str) or a not in ACTIONS for a in actions) or (actions and not project["aiEnabled"]):
            raise MecaError("Actions invalides ; une règle IA désactivée doit avoir une liste vide.")
        for field in ("monthlyTokenQuota", "monthlyUserTokenQuota"):
            if field in project and not integer(project[field]):
                raise MecaError("Les quotas doivent être des entiers positifs ou nuls.")
        if project["aiEnabled"] and (project.get("contextMode") not in ("LOCAL_GOVERNED", "SERVER_VERIFIED") or not isinstance(project.get("modelAlias"), str) or project.get("modelAlias") not in aliases):
            raise MecaError("Un projet IA actif doit référencer un modèle connu et un contexte valide.")
        if "allowFreeText" in project and type(project["allowFreeText"]) is not bool:
            raise MecaError("allowFreeText doit être true ou false.")
        if "allowedAgents" in project and (not isinstance(project["allowedAgents"], list) or any(not text(a) for a in project["allowedAgents"])):
            raise MecaError("allowedAgents doit être une liste d'identifiants non vides.")
        if "sonarProjectKey" in project and not sonar_key(project["sonarProjectKey"]):
            raise MecaError("sonarProjectKey invalide : 1..400 caractères A-Z a-z 0-9 _ . : -, non exclusivement numériques.")


def unique_object(pairs):
    """Refuse les doublons de clés JSON qui rendraient la politique ambiguë. MECA-002."""
    result = {}
    for key, value in pairs:
        if key in result:
            raise MecaError("Clé JSON dupliquée.")
        result[key] = value
    return result


def decode(data):
    """Charge un document UTF-8 borné sans modifier les octets utilisés pour sa signature. MECA-002."""
    if len(data) > MAX_SIZE:
        raise MecaError("Politique supérieure à 1 Mio.")
    try:
        return json.loads(data.decode("utf-8"), object_pairs_hook=unique_object)
    except (ValueError, UnicodeError):
        raise MecaError("Politique JSON UTF-8 invalide.") from None


def encode(policy):
    """Produit des octets UTF-8/LF déterministes pour la signature et le commit. MECA-003."""
    data = (json.dumps(policy, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")
    if len(data) > MAX_SIZE:
        raise MecaError("Politique supérieure à 1 Mio.")
    return data


def renew(policy, now=None):
    """Incrémente une seule fois la version et fixe exactement 24 heures en UTC. MECA-003."""
    result = deepcopy(policy)
    if not integer(result.get("policyVersion"), 1) or result["policyVersion"] == MAX_LONG:
        raise MecaError("Impossible d'incrémenter policyVersion.")
    issued = (now or utcnow()).astimezone(timezone.utc).replace(microsecond=0)
    result["policyVersion"] += 1
    result["issuedAt"] = issued.isoformat().replace("+00:00", "Z")
    result["expiresAt"] = (issued + timedelta(hours=24)).isoformat().replace("+00:00", "Z")
    return result


def load_keys(private_path, public_path):
    """Charge les formats Base64 DER PKCS8/X509 de GovernanceMain et vérifie leur association. MECA-003, MECA-006."""
    try:
        private = serialization.load_der_private_key(base64.b64decode(Path(private_path).read_bytes().strip(), validate=True), password=None)
        public = serialization.load_der_public_key(base64.b64decode(Path(public_path).read_bytes().strip(), validate=True))
        if not isinstance(private, Ed25519PrivateKey) or not isinstance(public, Ed25519PublicKey):
            raise ValueError()
        public.verify(private.sign(b"meca-key-pair"), b"meca-key-pair")
        return private, public
    except Exception:
        raise MecaError("Clés Ed25519 invalides ou non associées ; formats Base64 DER PKCS8/X509 requis.") from None


def verify(data, signature, public):
    """Vérifie la signature détachée avant toute édition de la politique existante. MECA-003."""
    try:
        public.verify(base64.b64decode(signature.strip(), validate=True), data)
    except Exception:
        raise MecaError("Signature de gouvernance invalide : aucune publication autorisée.") from None


def sign(data, private):
    """Signe exactement les octets du JSON et produit le format .sig attendu par ODS. MECA-003."""
    return base64.b64encode(private.sign(data))
