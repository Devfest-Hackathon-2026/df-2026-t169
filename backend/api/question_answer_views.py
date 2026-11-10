
import json
import logging
from datetime import datetime, date
from zoneinfo import ZoneInfo

from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_GET, require_POST
from google.cloud import firestore

from .authentication import require_firebase_auth
from .registration_permissions import require_registration_completed

logger = logging.getLogger(__name__)
JST = ZoneInfo("Asia/Tokyo")


@csrf_exempt
@require_POST
@require_firebase_auth
@require_registration_completed
def save_question_answer(request, question_id):
    """今日の問いへの選択回答を保存・更新する。"""

    # 問いIDの形式を検証
    try:
        parsed_date = date.fromisoformat(question_id)
        if parsed_date.isoformat() != question_id:
            raise ValueError()
    except ValueError:
        return JsonResponse(
            {"error": "Invalid questionId"},
            status=400,
        )

    # JSONの解析
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

    selected_index = payload.get("selectedOptionIndex")

    # boolはPythonではintの派生型なのでtypeで判定
    if type(selected_index) is not int or selected_index < 0:
        return JsonResponse(
            {"error": "Invalid selectedOptionIndex"},
            status=400,
        )

    uid = request.firebase_user["uid"]

    try:
        db = firestore.Client()

        question_ref = (
            db.collection("questions")
            .document(question_id)
        )

        answer_ref = (
            question_ref.collection("answers")
            .document(uid)
        )

        @firestore.transactional
        def save_answer(transaction):
            # 読み取りを先に完了する
            question_snap = question_ref.get(
                transaction=transaction
            )

            if not question_snap.exists:
                return "question_not_found"

            question = question_snap.to_dict() or {}

            today = datetime.now(JST).date().isoformat()

            # 投稿・回答できるのは公開当日のみ
            if question.get("publishDate") != today:
                return "answer_closed"

            if question_id != today:
                return "answer_closed"

            archive_until = question.get("archiveUntil")

            if (
                not isinstance(archive_until, str)
                or archive_until < today
            ):
                return "answer_closed"

            options = question.get("options")

            if (
                not isinstance(options, list)
                or len(options) not in (3, 4)
                or not all(
                    isinstance(option, str) and option.strip()
                    for option in options
                )
            ):
                return "invalid_question"

            if selected_index >= len(options):
                return "invalid_option"

            # 同じユーザーの既存回答を確認
            existing = answer_ref.get(
                transaction=transaction
            )

            answer_data = {
                "selectedOptionIndex": selected_index,
                "updatedAt": firestore.SERVER_TIMESTAMP,
            }

            if not existing.exists:
                answer_data["createdAt"] = (
                    firestore.SERVER_TIMESTAMP
                )

            transaction.set(
                answer_ref,
                answer_data,
                merge=True,
            )

            return "updated" if existing.exists else "created"

        result = save_answer(db.transaction())

        error_map = {
            "question_not_found": (
                404, "Question not found"
            ),
            "answer_closed": (
                403, "Answer period closed"
            ),
            "invalid_question": (
                503, "Question data invalid"
            ),
            "invalid_option": (
                400, "Invalid selectedOptionIndex"
            ),
        }

        if result in error_map:
            status_code, message = error_map[result]

            return JsonResponse(
                {"error": message},
                status=status_code,
            )

        return JsonResponse(
            {
                "questionId": question_id,
                "selectedOptionIndex": selected_index,
                "status": result,
            },
            status=201 if result == "created" else 200,
        )

    except Exception:
        logger.exception(
            "Failed to save question answer"
        )

        return JsonResponse(
            {"error": "Question answer unavailable"},
            status=503,
        )


def _get_question_data(db, question_id):
    """問いIDとFirestore上の問いデータを検証する。"""

    try:
        parsed = date.fromisoformat(question_id)

        if parsed.isoformat() != question_id:
            return None

    except (ValueError, TypeError):
        return None

    snapshot = (
        db.collection("questions")
        .document(question_id)
        .get()
    )

    if not snapshot.exists:
        return None

    data = snapshot.to_dict() or {}

    options = data.get("options")

    if (
        data.get("publishDate") != question_id
        or not isinstance(data.get("archiveUntil"), str)
        or not isinstance(options, list)
        or len(options) not in (3, 4)
        or not all(
            isinstance(option, str) and option.strip()
            for option in options
        )
    ):
        raise ValueError("Invalid question data")

    try:
        archive_date = date.fromisoformat(
            data["archiveUntil"]
        )

        if archive_date < parsed:
            raise ValueError("Invalid archive date")

    except (ValueError, TypeError):
        raise ValueError("Invalid question data")

    return data


@require_GET
@require_firebase_auth
@require_registration_completed
def get_my_question_answer(request, question_id):
    """ログインユーザー本人の選択回答を取得する。"""

    uid = request.firebase_user["uid"]

    try:
        db = firestore.Client()

        question = _get_question_data(
            db, question_id
        )

        if question is None:
            return JsonResponse(
                {"error": "Question not found"},
                status=404,
            )

        answer_ref = (
            db.collection("questions")
            .document(question_id)
            .collection("answers")
            .document(uid)
        )

        snapshot = answer_ref.get()

        if not snapshot.exists:
            return JsonResponse(
                {
                    "questionId": question_id,
                    "answered": False,
                    "selectedOptionIndex": None,
                },
                status=200,
            )

        answer = snapshot.to_dict() or {}
        index = answer.get("selectedOptionIndex")
        options = question["options"]

        if (
            type(index) is not int
            or not 0 <= index < len(options)
        ):
            logger.error(
                "Invalid saved answer: %s",
                question_id,
            )
            return JsonResponse(
                {"error": "Answer data invalid"},
                status=503,
            )

        return JsonResponse(
            {
                "questionId": question_id,
                "answered": True,
                "selectedOptionIndex": index,
            },
            status=200,
        )

    except Exception:
        logger.exception(
            "Failed to get own question answer"
        )
        return JsonResponse(
            {"error": "Question answer unavailable"},
            status=503,
        )


@require_GET
@require_firebase_auth
@require_registration_completed
def get_question_results(request, question_id):
    """公開期間内の問いの回答数・割合を集計する。"""

    today = datetime.now(JST).date().isoformat()

    try:
        db = firestore.Client()

        question = _get_question_data(
            db, question_id
        )

        if question is None:
            return JsonResponse(
                {"error": "Question not found"},
                status=404,
            )

        # 公開日〜アーカイブ期限だけ集計を公開
        if not (
            question_id <= today
            <= question["archiveUntil"]
        ):
            return JsonResponse(
                {"error": "Question results unavailable"},
                status=403,
            )

        options = question["options"]

        counts = [0] * len(options)

        answers = (
            db.collection("questions")
            .document(question_id)
            .collection("answers")
            .stream()
        )

        for snapshot in answers:
            data = snapshot.to_dict() or {}
            index = data.get("selectedOptionIndex")

            if (
                type(index) is int
                and 0 <= index < len(options)
            ):
                counts[index] += 1
            else:
                logger.warning(
                    "Skipping invalid answer: question=%s",
                    question_id,
                )

        total = sum(counts)

        results = []

        for index, option in enumerate(options):
            count = counts[index]

            percentage = (
                round(count / total * 100, 1)
                if total > 0
                else 0.0
            )

            results.append({
                "optionIndex": index,
                "optionText": option,
                "count": count,
                "percentage": percentage,
            })

        return JsonResponse(
            {
                "questionId": question_id,
                "totalAnswers": total,
                "results": results,
            },
            status=200,
            json_dumps_params={"ensure_ascii": False},
        )

    except Exception:
        logger.exception(
            "Failed to get question results"
        )
        return JsonResponse(
            {"error": "Question results unavailable"},
            status=503,
        )

