
import json
import logging
from datetime import datetime
from zoneinfo import ZoneInfo

from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_POST
from google.cloud import firestore

from .authentication import require_firebase_auth
from .registration_permissions import require_registration_completed
from .diagnosis_questions import TYPE_MAP

logger = logging.getLogger(__name__)
JST = ZoneInfo("Asia/Tokyo")


@csrf_exempt
@require_POST
@require_firebase_auth
@require_registration_completed
def create_post(request):
    """回答済みの当日の問いに対して投稿する。"""

    try:
        payload = json.loads(request.body)
    except (ValueError, UnicodeDecodeError):
        return JsonResponse(
            {"error": "Invalid JSON"},
            status=400,
        )

    if not isinstance(payload, dict):
        return JsonResponse(
            {"error": "JSON object required"},
            status=400,
        )

    question_id = payload.get("questionId")
    content = payload.get("content")
    option_index = payload.get("selectedOptionIndex")

    if not isinstance(question_id, str) or not question_id:
        return JsonResponse(
            {"error": "Invalid questionId"},
            status=400,
        )

    if not isinstance(content, str):
        return JsonResponse(
            {"error": "Invalid content"},
            status=400,
        )

    content = content.strip()

    if not 1 <= len(content) <= 1000:
        return JsonResponse(
            {"error": "Content must be 1-1000 characters"},
            status=400,
        )

    if type(option_index) is not int or option_index < 0:
        return JsonResponse(
            {"error": "Invalid selectedOptionIndex"},
            status=400,
        )

    uid = request.firebase_user["uid"]

    try:
        db = firestore.Client()

        question_ref = (
            db.collection("questions").document(question_id)
        )
        user_ref = db.collection("users").document(uid)

        # STEP 2で保存した本人の選択回答
        answer_ref = (
            question_ref.collection("answers").document(uid)
        )

        post_ref = db.collection("posts").document()

        @firestore.transactional
        def save_post(transaction):
            # 全ての読み取りを先に実行する
            question_snap = question_ref.get(
                transaction=transaction
            )
            user_snap = user_ref.get(
                transaction=transaction
            )
            answer_snap = answer_ref.get(
                transaction=transaction
            )

            if not question_snap.exists:
                return "question_not_found", None

            if not user_snap.exists:
                return "user_not_found", None

            question = question_snap.to_dict() or {}
            user = user_snap.to_dict() or {}

            if user.get("registrationState") != "Completed":
                return "registration_incomplete", None

            today = datetime.now(JST).date().isoformat()

            # 投稿可能なのは当日の問いのみ
            if (
                question_id != today
                or question.get("publishDate") != today
                or not isinstance(
                    question.get("archiveUntil"), str
                )
                or question["archiveUntil"] < today
            ):
                return "posting_closed", None

            options = question.get("options")

            if (
                not isinstance(options, list)
                or len(options) not in (3, 4)
                or not all(
                    isinstance(item, str) and item.strip()
                    for item in options
                )
            ):
                return "invalid_question", None

            if option_index >= len(options):
                return "invalid_option", None

            # 回答未保存の場合、投稿できない
            if not answer_snap.exists:
                return "answer_required", None

            answer = answer_snap.to_dict() or {}

            saved_index = answer.get("selectedOptionIndex")

            if (
                type(saved_index) is not int
                or not 0 <= saved_index < len(options)
            ):
                return "invalid_answer", None

            # Androidの選択値と保存済み回答が一致するか
            if option_index != saved_index:
                return "answer_mismatch", None

            # 診断タイプはサーバー側で確定する
            latest = user.get("latestDiagnosis") or {}

            type_code = (
                latest.get("typeCode")
                if isinstance(latest, dict)
                else None
            )

            if type_code not in TYPE_MAP:
                return "diagnosis_unavailable", None

            # 全ての検証が完了した後に投稿を作成
            transaction.create(
                post_ref,
                {
                    "uid": uid,
                    "questionId": question_id,
                    "typeCodeAtPost": type_code,
                    "content": content,
                    "selectedOptionIndex": saved_index,
                    "likeCount": 0,
                    "createdAt": firestore.SERVER_TIMESTAMP,
                },
            )

            return "created", type_code

        result, type_code = save_post(db.transaction())

        error_map = {
            "question_not_found": (
                404, "Question not found"
            ),
            "user_not_found": (
                404, "User not found"
            ),
            "registration_incomplete": (
                403, "Registration incomplete"
            ),
            "posting_closed": (
                403, "Posting period closed"
            ),
            "invalid_question": (
                503, "Question data invalid"
            ),
            "invalid_option": (
                400, "Invalid selectedOptionIndex"
            ),
            "answer_required": (
                409, "Answer required before posting"
            ),
            "invalid_answer": (
                409, "Saved answer invalid"
            ),
            "answer_mismatch": (
                409, "Selected option differs from saved answer"
            ),
            "diagnosis_unavailable": (
                409, "Diagnosis unavailable"
            ),
        }

        if result != "created":
            status_code, message = error_map.get(
                result,
                (503, "Post creation failed"),
            )
            return JsonResponse(
                {"error": message},
                status=status_code,
            )

        return JsonResponse(
            {
                "id": post_ref.id,
                "questionId": question_id,
                "typeCodeAtPost": type_code,
                "content": content,
                "selectedOptionIndex": option_index,
            },
            status=201,
            json_dumps_params={"ensure_ascii": False},
        )

    except Exception:
        logger.exception("Post creation failed")
        return JsonResponse(
            {"error": "Post service unavailable"},
            status=503,
        )

