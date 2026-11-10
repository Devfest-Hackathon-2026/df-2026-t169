import json
import unittest
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from django.test import RequestFactory

from api import diagnosis_views


def diagnosis_snapshot(diagnosis_id):
    return SimpleNamespace(
        id=diagnosis_id,
        to_dict=lambda: {
            "questionSetVersion": "v2.1",
            "typeCode": "GIPS",
            "styleAxisRawTotals": {},
            "styleAxisScores": {},
            "styleDirectionCodes": {},
            "boundaryFlags": {},
            "readinessScores": {},
            "completedAt": None,
        },
    )


class DiagnosisRetrievalTests(unittest.TestCase):
    def request(self, path):
        return RequestFactory().get(
            path,
            HTTP_AUTHORIZATION="Bearer test-token",
        )

    def test_latest_uses_completed_at_order_not_profile_cache(self):
        db = MagicMock()
        profile_ref = db.collection.return_value.document.return_value
        diagnoses = profile_ref.collection.return_value
        ordered_query = diagnoses.order_by.return_value
        query = ordered_query.limit.return_value
        query.stream.return_value = [diagnosis_snapshot("latest-id")]

        with patch(
            "api.authentication.auth.verify_id_token",
            return_value={"uid": "test-user"},
        ), patch.object(diagnosis_views, "get_firestore", return_value=db):
            response = diagnosis_views.get_latest_diagnosis(
                self.request("/diagnoses/latest")
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(json.loads(response.content)["id"], "latest-id")
        profile_ref.get.assert_not_called()
        diagnoses.order_by.assert_called_once_with(
            "completedAt",
            direction=diagnosis_views.firestore.Query.DESCENDING,
        )
        ordered_query.limit.assert_called_once_with(1)

    def test_history_defaults_to_latest_two(self):
        db = MagicMock()
        diagnoses = (
            db.collection.return_value.document.return_value.collection.return_value
        )
        ordered_query = diagnoses.order_by.return_value
        query = ordered_query.limit.return_value
        query.stream.return_value = [
            diagnosis_snapshot("newest-id"),
            diagnosis_snapshot("previous-id"),
        ]

        with patch(
            "api.authentication.auth.verify_id_token",
            return_value={"uid": "test-user"},
        ), patch.object(diagnosis_views, "get_firestore", return_value=db):
            response = diagnosis_views.get_diagnosis_history(
                self.request("/diagnoses/history")
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            [item["id"] for item in json.loads(response.content)["diagnoses"]],
            ["newest-id", "previous-id"],
        )
        diagnoses.order_by.assert_called_once_with(
            "completedAt",
            direction=diagnosis_views.firestore.Query.DESCENDING,
        )
        ordered_query.limit.assert_called_once_with(2)
