"""Recette des questions guidées avec réponses simulées et sans appels métier. MECA-007."""
import unittest

from meca.cli import Wizard
from meca.policy import encode, validate
from test_policy import fixture


class WizardTests(unittest.TestCase):
    """Couvre ajout multiple, édition, modèle, valeurs invalides et confirmation finale. MECA-004, MECA-007."""

    def wizard(self, answers):
        """Collecte les explications affichées et consomme une séquence déterministe de réponses. MECA-007."""
        iterator = iter(answers)
        self.output = []
        return Wizard(lambda _: next(iterator), self.output.append)

    def test_add_two_projects_with_each_property_explained(self):
        """Ajoute deux règles sans altérer la règle originale et expose chaque propriété. MECA-004, MECA-007."""
        p = fixture()
        original = encode(p["projects"])
        answers = ["42", "43", "oui", "CHAT,XRAY_REMEDIATION,AGENT_RUN", "coding", "20000", "5000", "implement-requirement", "LOCAL_GOVERNED", "oui", "sonar-new"]
        wizard = self.wizard(answers)
        wizard.project(p)
        self.assertEqual(original, encode(p["projects"][:1]))
        wizard = self.wizard(["44", "non", "-", "coding", "0", "-", "-", "LOCAL_GOVERNED", "non", "-"])
        wizard.project(p)
        validate(p, "https://gitlab.invalid")
        self.assertEqual([42, 43, 44], [r["projectId"] for r in p["projects"]])
        self.assertNotIn("sonarProjectKey", p["projects"][2])
        for name in ("projectId", "aiEnabled", "allowedActions", "modelAlias", "monthlyTokenQuota", "monthlyUserTokenQuota", "allowedAgents", "contextMode", "allowFreeText", "sonarProjectKey"):
            self.assertTrue(any(name in line for line in self.output), name)

    def test_modify_defaults_and_remove_optional_mapping(self):
        """Entrée conserve les propriétés tandis que '-' supprime explicitement les champs facultatifs. MECA-004, MECA-007."""
        p = fixture()
        self.wizard(["", "", "", "25000", "-", "", "", "", "-"]).project(p, p["projects"][0])
        validate(p, "https://gitlab.invalid")
        self.assertEqual(42, p["projects"][0]["projectId"])
        self.assertEqual(25000, p["projects"][0]["monthlyTokenQuota"])
        self.assertNotIn("sonarProjectKey", p["projects"][0])
        self.assertNotIn("monthlyUserTokenQuota", p["projects"][0])

    def test_add_model_rejects_duplicate_and_unknown_provider(self):
        """Les réponses invalides sont reproposées et aucun fournisseur alternatif n'est inventé. MECA-004, MECA-007."""
        p = fixture()
        self.wizard(["coding", "new-alias", "OTHER", "MISTRAL", "bad model", "new-model", "oui"]).model(p)
        validate(p, "https://gitlab.invalid")
        self.assertEqual(2, len(p["models"]))
        self.assertEqual("new-alias", p["models"][1]["alias"])
        self.assertTrue(any("ODS_MISTRAL_MODEL" in line for line in self.output))

    def test_cancel_preview_and_single_renewal_after_declined_confirmation(self):
        """Revenir de l'aperçu ne consomme aucune version et Annuler ne produit aucun candidat. MECA-003, MECA-007."""
        original = encode(fixture())
        self.assertIsNone(self.wizard(["0"]).edit(original, "https://gitlab.invalid"))
        candidate = self.wizard(["4", "5", "", "5", "PUBLIER"]).edit(original, "https://gitlab.invalid")
        self.assertEqual(8, candidate["policyVersion"])
        self.assertTrue(any("politique proposée" in line for line in self.output))

    def test_retry_invalid_boolean_and_integer(self):
        """Une valeur mal saisie ne devient jamais implicitement une autorisation ou un quota. MECA-004, MECA-007."""
        wizard = self.wizard(["peut-être", "non", "-1", "1.5", "50"])
        self.assertFalse(wizard.boolean("flag", "Explication", True))
        self.assertEqual(50, wizard.number("quota", "Explication", 0))

    def test_missing_free_text_permission_is_not_implicitly_enabled(self):
        """L'édition d'une règle historique ne transforme pas un champ absent en droit accordé. MECA-004, MECA-007."""
        p = fixture()
        del p["projects"][0]["allowFreeText"]
        self.wizard([""] * 9).project(p, p["projects"][0])
        self.assertFalse(p["projects"][0]["allowFreeText"])


if __name__ == "__main__":
    unittest.main()
