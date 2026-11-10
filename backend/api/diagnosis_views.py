import hashlib
import json
import logging
import re
import uuid

from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_GET, require_POST

from firebase_admin import firestore
from google.cloud import firestore as google_firestore

from .authentication import require_firebase_auth

from .profile_views import (
    get_firestore,
    check_email_verification,
)

from .diagnosis_questions import (
    QUESTION_SET_VERSION,
    TYPE_MAP,
    get_question_set,
)
from .diagnosis_service import score_diagnosis

@require_GET
def get_diagnosis_questions(request):
    """
    diagnosis_questions.pyから、ライフデザイン診断の20問を取得するAPI。

    GET /diagnoses/questions
    """

    question_set = get_question_set()

    return JsonResponse(
        question_set,
        status=200,
        json_dumps_params={"ensure_ascii": False},
    )


logger = logging.getLogger(__name__)

DIAGNOSIS_SCHEMA_VERSION = 1
DIAGNOSIS_ID_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def api_error(message, status=400):
    return JsonResponse(
        {"error": message},
        status=status,
    )


def diagnosis_to_response(snapshot):
    """Firestoreの診断ドキュメントをAndroid向けに変換"""
    data = snapshot.to_dict() or {}
    type_code = data["typeCode"]
    type_info = TYPE_MAP[type_code]

    completed_at = data.get("completedAt")

    return {
        "id": snapshot.id,
        "questionSetVersion": data["questionSetVersion"],
        "typeCode": type_code,
        "typeName": type_info["name"],
        "description": type_info["description"],
        "styleAxisRawTotals": data["styleAxisRawTotals"],
        "styleAxisScores": data["styleAxisScores"],
        "styleDirectionCodes": data["styleDirectionCodes"],
        "boundaryFlags": data["boundaryFlags"],
        "readinessScores": data["readinessScores"],
        "completedAt": (
            completed_at.isoformat()
            if completed_at is not None
            else None
        ),
    }


def profile_defaults(firebase_user):
    """プロフィールが存在しない場合の初期値"""
    return {
        "name": firebase_user.get("name")
                or firebase_user.get("email", "").split("@")[0],
        "bio": "",
        "avatarUrl": "",
        "createdAt": firestore.SERVER_TIMESTAMP,
    }


@require_GET
def get_diagnosis_questions(request):
    return JsonResponse(
        get_question_set(),
        json_dumps_params={"ensure_ascii": False},
    )


@csrf_exempt
@require_POST
@require_firebase_auth
def submit_diagnosis(request):
    """
    POST /diagnoses

    20問の回答を採点し、Firestoreに保存する。
    """

    try:
        payload = json.loads(request.body.decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        return api_error("Invalid JSON")

    if not isinstance(payload, dict):
        return api_error("JSON object is required")

    if payload.get("questionSetVersion") != QUESTION_SET_VERSION:
        return api_error("Unsupported questionSetVersion")

    key = payload.get("idempotencyKey")

    if not isinstance(key, str):
        return api_error("idempotencyKey is required")

    try:
        parsed_key = uuid.UUID(key)
    except (ValueError, AttributeError):
        return api_error("idempotencyKey must be a UUID")

    if str(parsed_key) != key:
        return api_error("idempotencyKey must be a canonical UUID")

    answers = payload.get("answers")

    try:
        result = score_diagnosis(answers)
    except ValueError as exc:
        return api_error(str(exc))

    uid = request.firebase_user["uid"]
    verification_error = check_email_verification(request.firebase_user)

    if verification_error is not None:
        return verification_error

    # 同じユーザー・同じキーなら常に同じ診断IDになる
    diagnosis_id = hashlib.sha256(
        f"{uid}\0{key}".encode("utf-8")
    ).hexdigest()

    # 同じキーで別の回答を送った場合に検出する
    answer_hash = hashlib.sha256(
        json.dumps(
            {
                "questionSetVersion": QUESTION_SET_VERSION,
                "answers": answers,
            },
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    ).hexdigest()

    db = get_firestore()

    profile_ref = db.collection("users").document(uid)
    diagnosis_ref = (
        profile_ref
        .collection("diagnoses")
        .document(diagnosis_id)
    )

    @google_firestore.transactional
    def save_diagnosis(transaction):
        existing = diagnosis_ref.get(transaction=transaction)
        profile = profile_ref.get(transaction=transaction)

        if existing.exists:
            saved = existing.to_dict()

            if saved.get("answerHash") != answer_hash:
                return "conflict"

            return "existing"

        diagnosis_data = {
            "schemaVersion": DIAGNOSIS_SCHEMA_VERSION,
            "questionSetVersion": QUESTION_SET_VERSION,
            "answerHash": answer_hash,
            "styleAxisRawTotals": result["styleAxisRawTotals"],
            "styleAxisScores": result["styleAxisScores"],
            "styleDirectionCodes": result["styleDirectionCodes"],
            "typeCode": result["typeCode"],
            "boundaryFlags": result["boundaryFlags"],
            "readinessScores": result["readinessScores"],
            "completedAt": firestore.SERVER_TIMESTAMP,
            "idempotencyKey": key,
        }

        type_info = TYPE_MAP[result["typeCode"]]

        latest_diagnosis = {
            "id": diagnosis_id,
            "typeCode": result["typeCode"],
            "description": type_info["description"],
        }

        transaction.create(diagnosis_ref, diagnosis_data)

        # 既存プロフィールがある場合だけ最新診断を反映する
        if profile.exists:
            transaction.set(
                profile_ref,
                {
                    "latestDiagnosis": latest_diagnosis,
                },
                merge=True,
            )

        # 新規ユーザーのプロフィールはここでは作成しない
        # 診断結果だけをサブコレクションに保存する

        return "created"

    try:
        save_status = save_diagnosis(db.transaction())

        if save_status == "conflict":
            return api_error(
                "idempotencyKey was already used with different answers",
                status=409,
            )

        saved_snapshot = diagnosis_ref.get()

        if not saved_snapshot.exists:
            logger.error("Diagnosis missing after transaction: %s", diagnosis_id)
            return api_error("Diagnosis could not be loaded", 500)

        response = diagnosis_to_response(saved_snapshot)

        return JsonResponse(
            response,
            status=201 if save_status == "created" else 200,
            json_dumps_params={"ensure_ascii": False},
        )

    except Exception:
        logger.exception("Failed to save diagnosis")
        return api_error("Diagnosis storage failed", status=500)

@require_GET
@require_firebase_auth
def get_latest_diagnosis(request):
    """GET /diagnoses/latest

    プロフィール未作成でも最新の診断結果を取得する。
    """

    uid = request.firebase_user["uid"]
    db = get_firestore()

    profile_ref = db.collection("users").document(uid)

    query = (
        profile_ref.collection("diagnoses")
        .order_by(
            "completedAt",
            direction=firestore.Query.DESCENDING,
        )
        .limit(1)
    )

    snapshots = list(query.stream())

    if not snapshots:
        return api_error("Diagnosis not found", 404)

    return JsonResponse(
        diagnosis_to_response(snapshots[0]),
        json_dumps_params={"ensure_ascii": False},
    )


@require_GET
@require_firebase_auth
def get_diagnosis_history(request):
    """GET /diagnoses/history?limit=2"""

    try:
        limit = int(request.GET.get("limit", "2"))
    except (ValueError, TypeError):
        return api_error("Invalid limit")

    if not 1 <= limit <= 20:
        return api_error("limit must be between 1 and 20")

    uid = request.firebase_user["uid"]
    db = get_firestore()

    query = (
        db.collection("users")
        .document(uid)
        .collection("diagnoses")
        .order_by(
            "completedAt",
            direction=firestore.Query.DESCENDING,
        )
        .limit(limit)
    )

    diagnoses = [
        diagnosis_to_response(snapshot)
        for snapshot in query.stream()
    ]

    return JsonResponse(
        {"diagnoses": diagnoses},
        json_dumps_params={"ensure_ascii": False},
    )


@require_GET
@require_firebase_auth
def get_diagnosis_by_id(request, diagnosis_id):
    """GET /diagnoses/<diagnosis_id>"""

    if not DIAGNOSIS_ID_PATTERN.fullmatch(diagnosis_id):
        return api_error("Invalid diagnosis ID")

    uid = request.firebase_user["uid"]
    db = get_firestore()

    snapshot = (
        db.collection("users")
        .document(uid)
        .collection("diagnoses")
        .document(diagnosis_id)
        .get()
    )

    if not snapshot.exists:
        return api_error("Diagnosis not found", 404)

    return JsonResponse(
        diagnosis_to_response(snapshot),
        json_dumps_params={"ensure_ascii": False},
    )

@csrf_exempt
@require_POST
@require_firebase_auth
def skip_initial_diagnosis(request):
    return JsonResponse(
        {"error": "Diagnosis is required"},
        status=403,
    )