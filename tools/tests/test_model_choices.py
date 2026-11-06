"""Validate separate text-smoke and working agent catalogues without provider calls. Exigences : DEVAI-198, DEVAI-199."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('choice_provision', Path(__file__).resolve().parents[2] / 'TestDOKERMan/provision.py')
provision = importlib.util.module_from_spec(spec)
spec.loader.exec_module(provision)


class ModelChoiceTest(unittest.TestCase):
    """Keep agent capabilities explicit while preserving the existing bounded smoke profile. Exigences : DEVAI-198, DEVAI-199."""

    def test_agent_catalogue_does_not_call_services(self):
        """Offer both configured providers with tools and retain the moderate OpenAI quota. Exigences : DEVAI-199."""
        with patch.object(provision, 'api') as api:
            providers, models, default = provision.model_catalog({'model': 'synthetic-mistral'}, agent_models=True)
            self.assertEqual(['OPENAI', 'MISTRAL'], providers)
            self.assertEqual('openai-coding', default)
            self.assertTrue(all(model['tools'] for model in models))
            self.assertEqual(2048, models[0]['maxOutputTokens'])
            self.assertEqual(10000, models[0]['monthlyProviderTokenQuota'])
            api.assert_not_called()

    def test_legacy_and_smoke_profiles_remain_distinct(self):
        """Keep one default Mistral model and the opt-in text smoke unchanged, rejecting conflicting modes. Exigences : DEVAI-196, DEVAI-198, DEVAI-199."""
        state = {'model': 'synthetic-mistral'}
        self.assertEqual(['MISTRAL'], provision.model_catalog(state)[0])
        _, models, default = provision.model_catalog(state, openai_smoke=True)
        self.assertEqual('openai-docker-smoke', default)
        self.assertFalse(models[0]['tools'])
        self.assertEqual(128, models[0]['maxOutputTokens'])
        with self.assertRaises(ValueError):
            provision.model_catalog(state, openai_smoke=True, agent_models=True)
