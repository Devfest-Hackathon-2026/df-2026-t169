
from django.http import JsonResponse
from django.views.decorators.http import require_http_methods
from google.cloud import firestore
from google.api_core.exceptions import (
    AlreadyExists,
    GoogleAPICallError,
    NotFound,
)
from django.views.decorators.csrf import csrf_exempt

from .authentication import require_firebase_auth
import json
from .registration_permissions import require_registration_completed
from .registration_permissions import get_registration_state
from .diagnosis_questions import TYPE_MAP


def get_firestore():
    """Firestoreクライアントを取得する"""
    return firestore.Client()


def check_email_verification(user):
    """パスワード認証でメール未確認なら拒否する"""
    firebase_info = user.get("firebase") or {}
    provider = firebase_info.get("sign_in_provider")

    if provider == "password" and not user.get("email_verified", False):
        return JsonResponse(
            {"error": "Email verification required"},
            status=403
        )

    return None


def profile_response(uid, user, data):
    """FirestoreとFirebaseの情報を統合して返す"""
    created_at = data.get("createdAt")

    return {
        "id": uid,
        "name": data.get("name", ""),
        "email": user.get("email") or "",
        "emailVerified": user.get("email_verified", False),
        "bio": data.get("bio", ""),
        "avatarUrl": data.get("avatarUrl", ""),
        "createdAt": (
            created_at.isoformat()
            if created_at is not None else None
        ),
        "latestDiagnosis": data.get("latestDiagnosis"),
        "diagnosisOnboarding": data.get("diagnosisOnboarding"),
    }

@csrf_exempt
@require_http_methods(["POST"])
@require_firebase_auth

def initialize_profile(request):
    """
    POST /users/me/initialize

    必須診断の保存後にだけ、
    ユーザー名を受け取ってプロフィールを作成する。
    """

    user = request.firebase_user
    uid = user["uid"]

    verification_error = check_email_verification(user)
    if verification_error is not None:
        return verification_error

    # リクエストの読み取り
    try:
        payload = json.loads(
            request.body.decode("utf-8")
        ) if request.body else {}
    except (ValueError, UnicodeDecodeError):
        return JsonResponse(
            {"error": "Invalid JSON"},
            status=400,
        )

    if not isinstance(payload, dict):
        return JsonResponse(
            {"error": "JSON object is required"},
            status=400,
        )

    db = get_firestore()
    profile_ref = db.collection("users").document(uid)

    try:
        state, diagnosis_id = get_registration_state(
            uid,
            db,
        )

        # 登録済みユーザーによる再送
        if state == "Completed":
            snapshot = profile_ref.get()

            return JsonResponse(
                profile_response(
                    uid,
                    user,
                    snapshot.to_dict() or {},
                ),
                status=200,
            )

        # 診断を完了していなければ登録不可
        if state != "PendingUsername" or not diagnosis_id:
            return JsonResponse(
                {"error": "Diagnosis not completed"},
                status=409,
            )

        requested_name = payload.get("name")

        if not isinstance(requested_name, str):
            return JsonResponse(
                {"error": "Username is required"},
                status=400,
            )

        name = requested_name.strip()

        if (
            not 1 <= len(name) <= 30
            or any(
                ord(ch) < 32 or ord(ch) == 127
                for ch in name
            )
        ):
            return JsonResponse(
                {"error": "Invalid username"},
                status=400,
            )


        
        # 初回プロフィール登録時の自己紹介
        # 未指定の場合は空文字として扱う
        requested_bio = payload.get("bio", "")

        if not isinstance(requested_bio, str):
            return JsonResponse(
                {"error": "Bio must be a string"},
                status=400,
            )

        bio = requested_bio.strip()

        if (
            len(bio) > 120
            or any(
                ord(ch) < 32 and ch not in "\n\r\t"
                or ord(ch) == 127
                for ch in bio
            )
        ):
            return JsonResponse(
                {"error": "Invalid bio"},
                status=400,
            )


        diagnosis_ref = (
            profile_ref.collection("diagnoses")
            .document(diagnosis_id)
        )

        @firestore.transactional
        def create_profile(transaction):
            # Firestoreの読み取りを先に実行
            profile_snapshot = profile_ref.get(
                transaction=transaction
            )

            diagnosis_snapshot = diagnosis_ref.get(
                transaction=transaction
            )

            if not diagnosis_snapshot.exists:
                return "diagnosis_missing"

            # 再送で作成済みの場合
            if profile_snapshot.exists:
                existing = profile_snapshot.to_dict() or {}

                if existing.get("registrationState") == "Completed":
                    return "existing"

                # 不完全な旧プロフィールを自動的に
                # 登録完了扱いしない
                return "unexpected_profile"

            diagnosis = diagnosis_snapshot.to_dict() or {}
            type_code = diagnosis.get("typeCode")

            if type_code not in TYPE_MAP:
                return "diagnosis_invalid"

            latest_diagnosis = {
                "id": diagnosis_snapshot.id,
                "typeCode": type_code,
                "description": TYPE_MAP[type_code]["description"],
            }

            transaction.create(
                profile_ref,
                {
                    "name": name,
                    "bio": bio,
                    "avatarUrl": "",
                    "createdAt": firestore.SERVER_TIMESTAMP,
                    "latestDiagnosis": latest_diagnosis,
                    "initialDiagnosisId": diagnosis_snapshot.id,
                    "registrationState": "Completed",
                    "diagnosisOnboarding": "completed",
                    "registrationCompletedAt": firestore.SERVER_TIMESTAMP,
                },
            )

            return "created"

        result = create_profile(db.transaction())

        if result == "diagnosis_missing":
            return JsonResponse(
                {"error": "Diagnosis not found"},
                status=409,
            )

        if result == "diagnosis_invalid":
            return JsonResponse(
                {"error": "Invalid diagnosis result"},
                status=409,
            )

        if result == "unexpected_profile":
            return JsonResponse(
                {
                    "error": (
                        "Incomplete profile already exists. "
                        "Reset the test account before retrying."
                    )
                },
                status=409,
            )

        snapshot = profile_ref.get()

        if not snapshot.exists:
            return JsonResponse(
                {"error": "Profile not found"},
                status=503,
            )

        return JsonResponse(
            profile_response(
                uid,
                user,
                snapshot.to_dict() or {},
            ),
            status=201 if result == "created" else 200,
        )

    except GoogleAPICallError:
        return JsonResponse(
            {"error": "Database unavailable"},
            status=503,
        )

    except Exception:
        import logging
        logging.getLogger(__name__).exception(
            "Profile initialization failed"
        )

        return JsonResponse(
            {"error": "Profile initialization failed"},
            status=503,
        )



@require_http_methods(["GET"])
@require_firebase_auth
@require_registration_completed
def my_profile(request):
    """ログイン済みユーザーのプロフィールを取得"""

    user = request.firebase_user
    uid = user["uid"]

    verification_error = check_email_verification(user)
    if verification_error is not None:
        return verification_error

    try:
        db = get_firestore()
        snapshot = db.collection("users").document(uid).get()

        if not snapshot.exists:
            return JsonResponse(
                {"error": "Profile not found"},
                status=404
            )

        return JsonResponse(
            profile_response(uid, user, snapshot.to_dict())
        )

    except GoogleAPICallError:
        return JsonResponse(
            {"error": "Database unavailable"},
            status=503
        )

@csrf_exempt
@require_http_methods(["PATCH"])
@require_firebase_auth
@require_registration_completed
def update_my_profile(request):

    # ① Firebase認証済みユーザーのUIDを取得
    user = request.firebase_user
    uid = user["uid"]

    # ② メール認証状態を確認
    verification_error = check_email_verification(user)
    if verification_error is not None:
        return verification_error

    # ③ Androidから送信されたJSONを取得
    try:
        data = json.loads(request.body)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return JsonResponse(
            {"error": "Invalid JSON"},
            status=400,
        )

    if not isinstance(data, dict):
        return JsonResponse(
            {"error": "JSON object required"},
            status=400,
        )

    # ④ 更新可能なフィールドだけを受け付ける
    if set(data.keys()) != {"name", "bio"}:
        return JsonResponse(
            {"error": "Only name and bio are allowed"},
            status=400,
        )

    name = data["name"]
    bio = data["bio"]

    # ⑤ 入力値の検証
    if not isinstance(name, str) or not isinstance(bio, str):
        return JsonResponse(
            {"error": "name and bio must be strings"},
            status=400,
        )

     # ユーザー名・自己紹介の正規化
    name = name.strip()
    bio = bio.strip()

    # ユーザー名：1〜30文字、制御文字禁止
    if (
        not 1 <= len(name) <= 30
        or any(
            ord(ch) < 32 or ord(ch) == 127
            for ch in name
        )
    ):
        return JsonResponse(
            {"error": "Invalid username (1-30 characters)"},
            status=400,
        )

    # 自己紹介：最大120文字、改行・タブは許可
    if (
        len(bio) > 120
        or any(
            (ord(ch) < 32 and ch not in "\n\r\t")
            or ord(ch) == 127
            for ch in bio
        )
    ):
        return JsonResponse(
            {"error": "Invalid bio (maximum 120 characters)"},
            status=400,
        )


    # ⑥ Firestoreの本人のドキュメントを更新
    try:
        db = get_firestore()
        doc_ref = db.collection("users").document(uid)

        doc_ref.update({
            "name": name,
            "bio": bio,
        })

        # ⑦ 更新後のプロフィールを取得
        snapshot = doc_ref.get()

        return JsonResponse(
            profile_response(uid, user, snapshot.to_dict()),
            status=200,
        )

    except NotFound:
        return JsonResponse(
            {"error": "Profile not found"},
            status=404,
        )

    except GoogleAPICallError:
        return JsonResponse(
            {"error": "Database unavailable"},
            status=503,
        )
