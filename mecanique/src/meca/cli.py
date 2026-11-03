"""Questions guidées et aperçu avant publication de la politique d'organisation. MECA-004, MECA-006."""
import argparse
import base64
from copy import deepcopy
import difflib
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from .policy import ACTIONS, MAX_LONG, MecaError, decode, encode, load_keys, renew, sign, sonar_key, utcnow, validate, verify
from .repository import Repository


class Wizard:
    """Édite plusieurs règles dans une copie mémoire avec questions explicatives pour chaque propriété. MECA-004."""

    def __init__(self, read=input, write=print):
        """Injecte les entrées/sorties pour tester l'interaction sans terminal ni service réel. MECA-007."""
        self.read, self.write = read, write

    def ask(self, name, explanation, default, parse):
        """Affiche l'aide et repropose la question tant que la valeur n'est pas valide. MECA-004."""
        self.write(f"\n{name} — {explanation}")
        while True:
            raw = self.read(f"Valeur [{default}] : ").strip()
            try:
                return parse(raw if raw else str(default))
            except (ValueError, MecaError):
                self.write("Valeur invalide. Respecter les possibilités indiquées ; Ctrl+C pour annuler.")

    def boolean(self, name, explanation, default):
        """Explique une propriété booléenne et accepte oui/non ou true/false. MECA-004."""
        def parse(raw):
            """Convertit uniquement les réponses booléennes explicites. MECA-004."""
            if raw.lower() in ("oui", "o", "true"):
                return True
            if raw.lower() in ("non", "n", "false"):
                return False
            raise ValueError()
        return self.ask(name, explanation + " Valeurs : oui / non.", "oui" if default else "non", parse)

    def number(self, name, explanation, default, minimum=0):
        """Demande un entier borné compatible avec les identifiants et quotas Java. MECA-004."""
        def parse(raw):
            """Rejette les nombres négatifs, décimaux ou hors plage long. MECA-004."""
            if not re.fullmatch(r"[0-9]+", raw) or not minimum <= int(raw) <= MAX_LONG:
                raise ValueError()
            return int(raw)
        return self.ask(name, explanation + f" Entier entre {minimum} et {MAX_LONG}.", default, parse)

    def choice(self, name, explanation, default, values):
        """Demande une valeur explicite parmi le catalogue actuellement configuré. MECA-004."""
        def parse(raw):
            """Ne sélectionne aucune valeur inconnue du catalogue proposé. MECA-004."""
            if raw not in values:
                raise ValueError()
            return raw
        return self.ask(name, explanation + " Valeurs : " + ", ".join(values) + ".", default, parse)

    def model(self, policy):
        """Ajoute un modèle Mistral sans renommer les alias déjà référencés par les projets. MECA-004."""
        aliases = {m["alias"] for m in policy["models"]}
        def alias(raw):
            """Exige un nouvel alias lisible et non ambigu. MECA-004."""
            if not re.fullmatch(r"[A-Za-z0-9_.:-]{1,128}", raw) or raw in aliases:
                raise ValueError()
            return raw
        def model_id(raw):
            """Valide la forme de l'identifiant sans inventer sa disponibilité chez Mistral. MECA-004."""
            if not re.fullmatch(r"[A-Za-z0-9_.:-]{1,128}", raw):
                raise ValueError()
            return raw
        model = {"alias": self.ask("alias", "Nom interne unique, 1..128 caractères lettres/chiffres _ . : -. Doit correspondre à ODS_MODEL_ALIAS pour les projets qui l'utilisent.", "", alias)}
        model["provider"] = self.choice("provider", "L'extension actuelle accepte uniquement Mistral.", "MISTRAL", ["MISTRAL"])
        model["model"] = self.ask("model", "Identifiant exact retourné par l'API Mistral /v1/models ; doit correspondre à ODS_MISTRAL_MODEL. Aucun appel Mistral n'est réalisé ici.", "", model_id)
        model["tools"] = self.boolean("tools", "Le modèle prend-il en charge les appels d'outils ? Nécessaire pour AGENT_RUN.", True)
        policy["models"].append(model)
        self.write("Modèle ajouté au brouillon. Cela ne change pas le modèle embarqué dans les VSIX existants.")

    def project(self, policy, existing=None):
        """Questionne chaque propriété d'une règle et conserve les autres projets inchangés. MECA-004."""
        rule = deepcopy(existing) if existing else {}
        if existing:
            self.write(f"Modification de projectId={rule['projectId']} (identifiant conservé). Entrée conserve la valeur affichée.")
        else:
            while True:
                project_id = self.number("projectId", "Identifiant numérique du projet GitLab, pas son nom ni l'ID du dépôt de gouvernance.", "", 1)
                if not any(p["projectId"] == project_id for p in policy["projects"]):
                    rule["projectId"] = project_id
                    break
                self.write("Ce projet existe déjà ; choisir Modifier pour le changer.")
        rule["aiEnabled"] = self.boolean("aiEnabled", "Autoriser l'IA sur ce projet ? Non conserve les analyses hors IA mais impose allowedActions vide.", rule.get("aiEnabled", False))
        def actions(raw):
            """Refuse les actions inconnues, dupliquées ou incompatibles avec une IA désactivée. MECA-004."""
            result = [] if raw == "-" else [v.strip() for v in raw.split(",")]
            if len(set(result)) != len(result) or any(v not in ACTIONS for v in result) or (result and not rule["aiEnabled"]):
                raise ValueError()
            return result
        action_help = "; ".join(f"{k} : {v}" for k, v in ACTIONS.items())
        default_actions = ",".join(rule.get("allowedActions", [])) if rule["aiEnabled"] else ""
        rule["allowedActions"] = self.ask("allowedActions", "Actions séparées par virgules ; '-' = aucune. " + action_help, default_actions or "-", actions)
        aliases = [m["alias"] for m in policy["models"]]
        rule["modelAlias"] = self.choice("modelAlias", "Alias du catalogue signé utilisé par ce projet ; doit correspondre au profil de l'extension.", rule.get("modelAlias", aliases[0]), aliases)
        rule["monthlyTokenQuota"] = self.number("monthlyTokenQuota", "Plafond mensuel de tokens du projet ; 0 n'est pas illimité.", rule.get("monthlyTokenQuota", 100000))
        def optional_quota(raw):
            """Conserve la possibilité d'omettre le plafond utilisateur des politiques historiques. MECA-004."""
            if raw == "-":
                return None
            if not raw.isascii() or not raw.isdigit() or int(raw) > MAX_LONG:
                raise ValueError()
            return int(raw)
        quota = self.ask("monthlyUserTokenQuota", "Plafond mensuel par utilisateur ; entier >=0, '-' = champ absent. Ne relève pas le plafond du projet.", rule.get("monthlyUserTokenQuota", "-"), optional_quota)
        if quota is None:
            rule.pop("monthlyUserTokenQuota", None)
        else:
            rule["monthlyUserTokenQuota"] = quota
        def agents(raw):
            """Valide une liste explicite d'agents sans accorder un joker. MECA-004."""
            result = [] if raw == "-" else [v.strip() for v in raw.split(",")]
            if len(set(result)) != len(result) or any(not re.fullmatch(r"[A-Za-z0-9_.:-]{1,128}", v) for v in result):
                raise ValueError()
            return result
        rule["allowedAgents"] = self.ask("allowedAgents", "Identifiants d'agents séparés par virgules, par exemple implement-requirement ; '-' = aucun. Ne remplace pas l'action AGENT_RUN.", ",".join(rule.get("allowedAgents", [])) or "-", agents)
        rule["contextMode"] = self.choice("contextMode", "LOCAL_GOVERNED autorise les sources locales filtrées ; SERVER_VERIFIED utilise des sources GitLab sur commit immuable.", rule.get("contextMode", "LOCAL_GOVERNED"), ["LOCAL_GOVERNED", "SERVER_VERIFIED"])
        rule["allowFreeText"] = self.boolean("allowFreeText", "Autoriser les messages textuels ? Les corrections automatiques actuelles en produisent aussi. Oui ne supprime pas leurs limites de taille.", rule.get("allowFreeText", False if existing else True))
        def sonar(raw):
            """Permet un mapping dynamique valide ou sa suppression explicite. MECA-004."""
            if raw == "-":
                return None
            if not sonar_key(raw):
                raise ValueError()
            return raw
        key = self.ask("sonarProjectKey", "Clé exacte Sonar, 1..400 caractères A-Z a-z 0-9 _ . : -, non exclusivement numériques. Nécessite ODS >=0.6.0 ; '-' = mapping embarqué de repli.", rule.get("sonarProjectKey", "-"), sonar)
        if key is None:
            rule.pop("sonarProjectKey", None)
        else:
            rule["sonarProjectKey"] = key
        if existing:
            policy["projects"][policy["projects"].index(existing)] = rule
        else:
            policy["projects"].append(rule)
        self.write("Projet enregistré dans le brouillon, pas encore publié.")

    def edit(self, original, instance):
        """Autorise plusieurs éditions avant un seul renouvellement/version et un aperçu final approuvé. MECA-003, MECA-004."""
        policy = deepcopy(decode(original))
        while True:
            self.write("\n1 Ajouter un projet | 2 Modifier un projet | 3 Ajouter un modèle | 4 Voir le brouillon | 5 Publier / renouveler 24h | 0 Annuler")
            action = self.read("Choix : ").strip()
            if action == "0":
                return None
            if action == "1":
                self.project(policy)
            elif action == "2":
                for p in policy["projects"]:
                    self.write(f"Projet {p['projectId']} : IA={p['aiEnabled']}, Sonar={p.get('sonarProjectKey', '(repli)')}")
                if not policy["projects"]:
                    self.write("Aucun projet à modifier.")
                    continue
                selected = self.choice("projectId", "Projet existant à modifier.", "", [str(p["projectId"]) for p in policy["projects"]])
                self.project(policy, next(p for p in policy["projects"] if str(p["projectId"]) == selected))
            elif action == "3":
                self.model(policy)
            elif action == "4":
                self.write(json.dumps(policy, ensure_ascii=False, indent=2))
            elif action == "5":
                candidate = renew(policy)
                validate(candidate, instance)
                diff = difflib.unified_diff(original.decode("utf-8").splitlines(), encode(candidate).decode("utf-8").splitlines(), fromfile="politique distante", tofile="politique proposée", lineterm="")
                self.write("\n".join(diff))
                self.write("La signature sera recalculée. Seuls ai-project-policy.json et ai-project-policy.sig seront commités et poussés.")
                if self.read("Taper PUBLIER pour confirmer, Entrée pour revenir : ").strip() == "PUBLIER":
                    return candidate
            else:
                self.write("Choix inconnu.")


def generate_keys(directory):
    """Crée une paire Ed25519 distincte sans écraser une clé déjà présente. MECA-003, MECA-006."""
    target = Path(directory)
    target.mkdir(parents=True, exist_ok=True)
    private_path, public_path = target / "signing.key", target / "public-key.txt"
    if private_path.exists() or public_path.exists():
        raise MecaError("Le répertoire contient déjà une clé ; aucune clé écrasée.")
    key = Ed25519PrivateKey.generate()
    private = key.private_bytes(serialization.Encoding.DER, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    with os.fdopen(os.open(private_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as stream:
        stream.write(base64.b64encode(private))
    with public_path.open("xb") as stream:
        stream.write(base64.b64encode(public))


def main(argv=None, read=input, write=print):
    """Expose un assistant autonome ; n'effectue aucun appel Sonar, Xray ou Mistral. MECA-001, MECA-004."""
    parser = argparse.ArgumentParser(description="Mécanique — assistant de politique GitLab signée (ODS >=0.6.0 pour sonarProjectKey).")
    commands = parser.add_subparsers(dest="command", required=True)
    keys = commands.add_parser("keys", help="Créer une paire de clés ; ne pas remplacer la paire déjà approuvée du VSIX.")
    keys.add_argument("directory")
    edit = commands.add_parser("edit", help="Modifier et publier les deux fichiers existants de gouvernance.")
    edit.add_argument("--repository", required=True, help="URL HTTPS/SSH du dépôt de gouvernance, sans token.")
    edit.add_argument("--branch", default="main", help="Valeur ODS_GOVERNANCE_REF ; branche existante, défaut main.")
    edit.add_argument("--gitlab-url", required=True, help="Valeur exacte ODS_GITLAB_URL du profil.")
    edit.add_argument("--private-key", required=True, type=Path)
    edit.add_argument("--public-key", required=True, type=Path, help="Fichier contenant ODS_POLICY_PUBLIC_KEY (clé simple Base64 DER).")
    edit.add_argument("--author-name", help="Identité du commit ; sinon configuration Git existante.")
    edit.add_argument("--author-email", help="Adresse de commit ; sinon configuration Git existante.")
    args = parser.parse_args(argv)
    try:
        if args.command == "keys":
            generate_keys(args.directory)
            write("Paire créée. Garder signing.key hors Git. Une nouvelle clé publique exige une configuration VSIX correspondante.")
            return 0
        parsed = urlsplit(args.gitlab_url)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise MecaError("--gitlab-url doit être une URL HTTPS sans credential, query ou fragment.")
        private, public = load_keys(args.private_key, args.public_key)
        with Repository(args.repository, args.branch, args.author_name, args.author_email) as repository:
            original, signature = repository.read()
            verify(original, signature, public)
            policy = decode(original)
            validate(policy, args.gitlab_url, allow_expired=True)
            write(f"Politique v{policy['policyVersion']} vérifiée sur la branche {args.branch}. Les dates seront renouvelées à la publication, même si la politique est expirée.")
            candidate = Wizard(read, write).edit(original, args.gitlab_url)
            if candidate is None:
                write("Annulé : aucun commit ni push.")
                return 0
            validate(candidate, args.gitlab_url)
            document = encode(candidate)
            detached = sign(document, private)
            verify(document, detached, public)
            commit = repository.publish(document, detached, candidate["policyVersion"])
            write(f"Publication confirmée : commit {commit}, policyVersion={candidate['policyVersion']}, expiresAt={candidate['expiresAt']}.")
        return 0
    except (KeyboardInterrupt, EOFError):
        write("Annulé : vérifier l'état distant si l'interruption a eu lieu pendant le push.")
        return 130
    except (MecaError, OSError) as error:
        write(str(error) if isinstance(error, MecaError) else "Erreur de fichier ou permissions locales ; aucune réussite annoncée.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
