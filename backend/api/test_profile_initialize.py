"""Initialize-profile contract tests; no Firebase or Firestore network calls."""
import json
import unittest
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from django.conf import settings
if not settings.configured:
    settings.configure(DEFAULT_CHARSET="utf-8", USE_TZ=True)
from django.test import RequestFactory
from api import profile_views


class InitializeBioTests(unittest.TestCase):
    def call_initialize(self, payload, state="PendingUsername"):
        db = MagicMock()
        profile = db.collection.return_value.document.return_value
        diagnosis = profile.collection.return_value.document.return_value
        diagnosis.get.return_value = SimpleNamespace(exists=True, id="initial", to_dict=lambda: {"typeCode": "GIPS"})
        profile.get.side_effect = [
            SimpleNamespace(exists=False),
            SimpleNamespace(exists=True, to_dict=lambda: {"name": "名前", "bio": payload.get("bio", "")}),
        ]
        request = RequestFactory().post("/users/me/initialize", json.dumps(payload), content_type="application/json", HTTP_AUTHORIZATION="Bearer synthetic-unit-test")
        with patch("api.authentication.auth.verify_id_token", return_value={"uid": "test", "email_verified": True}), \
             patch.object(profile_views, "get_firestore", return_value=db), \
             patch.object(profile_views, "get_registration_state", return_value=(state, "initial")), \
             patch.object(profile_views.firestore, "transactional", side_effect=lambda fn: fn):
            response = profile_views.initialize_profile(request)
        return response, db.transaction.return_value

    def test_legacy_name_only_request_saves_empty_bio(self):
        response, tx = self.call_initialize({"name": "名前"})
        self.assertEqual(response.status_code, 201)
        self.assertEqual(tx.create.call_args.args[1]["bio"], "")
        self.assertEqual(tx.create.call_args.args[1]["initialDiagnosisId"], "initial")
        self.assertEqual(tx.create.call_args.args[1]["registrationState"], "Completed")

    def test_optional_multiline_bio_is_saved_and_returned(self):
        bio = "自己紹介\n2行目"
        response, tx = self.call_initialize({"name": "  名前  ", "bio": bio})
        self.assertEqual(response.status_code, 201)
        self.assertEqual(tx.create.call_args.args[1]["bio"], bio)
        self.assertEqual(tx.create.call_args.args[1]["name"], "名前")
        self.assertEqual(json.loads(response.content)["bio"], bio)

    def test_120_unicode_characters_are_allowed(self):
        response, tx = self.call_initialize({"name": "名前", "bio": "😀" * 120})
        self.assertEqual(response.status_code, 201)
        self.assertEqual(len(tx.create.call_args.args[1]["bio"]), 120)

    def test_oversized_and_non_string_bio_never_write(self):
        for bio in ["あ" * 121, None, 123, [], {}]:
            with self.subTest(bio_type=type(bio).__name__):
                response, tx = self.call_initialize({"name": "名前", "bio": bio})
                self.assertEqual(response.status_code, 400)
                tx.create.assert_not_called()

    def test_diagnosis_gate_is_unchanged(self):
        response, tx = self.call_initialize({"name": "名前", "bio": "紹介"}, state="PendingDiagnosis")
        self.assertEqual(response.status_code, 409)
        tx.create.assert_not_called()
