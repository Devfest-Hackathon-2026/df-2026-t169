
import logging
from functools import wraps

from django.http import JsonResponse
from google.cloud import firestore

logger = logging.getLogger(__name__)


def get_registration_state(uid, db):
    """
    登録状態を3種類で返す。

    PendingDiagnosis: 診断未完了
    PendingUsername: 診断済み、名前未登録
    Completed: 登録完了
    """

    profile_ref = db.collection("users").document(uid)
    profile_snapshot = profile_ref.get()

    profile = (
        profile_snapshot.to_dict() or {}
        if profile_snapshot.exists
        else {}
    )

    # 登録完了済みなら、初回診断の実在を確認
    if profile.get("registrationState") == "Completed":
        diagnosis_id = profile.get("initialDiagnosisId")

        if not isinstance(diagnosis_id, str) or not diagnosis_id:
            raise ValueError("Initial diagnosis ID missing")

        diagnosis_snapshot = (
            profile_ref.collection("diagnoses")
            .document(diagnosis_id)
            .get()
        )

        if not diagnosis_snapshot.exists:
            raise ValueError("Initial diagnosis not found")

        return "Completed", diagnosis_id

    # 登録未完了なら、保存済み診断を探す
    diagnoses = list(
        profile_ref.collection("diagnoses")
        .order_by(
            "completedAt",
            direction=firestore.Query.DESCENDING,
        )
        .limit(1)
        .stream()
    )

    if diagnoses:
        return "PendingUsername", diagnoses[0].id

    return "PendingDiagnosis", None


def require_registration_completed(view_func):
    """
    Firebase認証済みユーザーについて、
    登録完了を確認してから通常APIを実行する。
    """

    @wraps(view_func)
    def wrapper(request, *args, **kwargs):
        user = getattr(request, "firebase_user", None)

        if not user or not user.get("uid"):
            return JsonResponse(
                {"error": "Authentication required"},
                status=401,
            )

        try:
            db = firestore.Client()

            state, _ = get_registration_state(
                user["uid"],
                db,
            )

        except Exception:
            logger.exception(
                "Registration authorization failed"
            )

            return JsonResponse(
                {"error": "Registration check unavailable"},
                status=503,
            )

        if state != "Completed":
            return JsonResponse(
                {
                    "error": "Registration incomplete",
                    "registrationState": state,
                },
                status=403,
            )

        return view_func(request, *args, **kwargs)

    return wrapper

