
import logging
from datetime import datetime
from zoneinfo import ZoneInfo

from django.http import JsonResponse
from django.views.decorators.http import require_GET
from google.cloud import firestore

from .authentication import require_firebase_auth
from .registration_permissions import require_registration_completed

logger = logging.getLogger(__name__)

JST = ZoneInfo("Asia/Tokyo")


@require_GET
@require_firebase_auth
@require_registration_completed
def get_today_question(request):
    """日本時間の今日の問いを取得する。"""

    today = datetime.now(JST).date().isoformat()

    try:
        db = firestore.Client()

        # ドキュメントIDを公開日とする
        snapshot = (
            db.collection("questions")
            .document(today)
            .get()
        )

        if not snapshot.exists:
            return JsonResponse(
                {"error": "Today's question not found"},
                status=404,
            )

        data = snapshot.to_dict() or {}

        # 登録情報と日付の整合性を検証
        if data.get("publishDate") != today:
            logger.error(
                "Question publish date mismatch: %s",
                snapshot.id,
            )
            return JsonResponse(
                {"error": "Question data invalid"},
                status=503,
            )

        archive_until = data.get("archiveUntil")
        text = data.get("text")
        options = data.get("options")

        if (
            not isinstance(text, str)
            or not text.strip()
            or not isinstance(options, list)
            or len(options) not in (3, 4)
            or not all(
                isinstance(item, str) and item.strip()
                for item in options
            )
            or not isinstance(archive_until, str)
            or archive_until < today
        ):
            logger.error(
                "Invalid question data: %s",
                snapshot.id,
            )
            return JsonResponse(
                {"error": "Question data invalid"},
                status=503,
            )

        return JsonResponse(
            {
                "id": snapshot.id,
                "text": text,
                "options": options,
                "publishDate": today,
                "archiveUntil": archive_until,
                "canPost": True,
            },
            status=200,
            json_dumps_params={"ensure_ascii": False},
        )

    except Exception:
        logger.exception("Failed to get today's question")

        return JsonResponse(
            {"error": "Question service unavailable"},
            status=503,
        )


@require_GET
@require_firebase_auth
@require_registration_completed
def get_archived_questions(request):
    """公開中のアーカイブ対象の問いを取得する。"""

    today = datetime.now(
        ZoneInfo("Asia/Tokyo")
    ).date().isoformat()

    try:
        db = firestore.Client()

        # アーカイブ期限が切れていない問いを検索
        documents = (
            db.collection("questions")
            .where(
                "archiveUntil",
                ">=",
                today,
            )
            .stream()
        )

        questions = []

        for doc in documents:
            data = doc.to_dict() or {}

            publish_date = data.get("publishDate")
            archive_until = data.get("archiveUntil")
            question_text = data.get("text")
            options = data.get("options")

            # 不正なデータや未公開の問いを除外
            if (
                not isinstance(publish_date, str)
                or not isinstance(archive_until, str)
                or not publish_date <= today <= archive_until
                or not isinstance(question_text, str)
                or not question_text.strip()
                or not isinstance(options, list)
                or len(options) not in (3, 4)
                or not all(
                    isinstance(option, str) and option.strip()
                    for option in options
                )
            ):
                continue

            questions.append({
                "id": doc.id,
                "text": question_text,
                "options": options,
                "tag": data.get("tag", ""),
                "reasons": data.get("reasons", []),
                "publishDate": publish_date,
                "archiveUntil": archive_until,
                "canPost": publish_date == today,
            })

        questions.sort(
            key=lambda q: q["publishDate"],
            reverse=True,
        )

        return JsonResponse(
            {"questions": questions},
            status=200,
            json_dumps_params={"ensure_ascii": False},
        )

    except Exception:
        logging.getLogger(__name__).exception(
            "Failed to get archived questions"
        )

        return JsonResponse(
            {"error": "Question archive unavailable"},
            status=503,
        )
