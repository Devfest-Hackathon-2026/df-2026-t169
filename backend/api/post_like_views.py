
import logging
from datetime import date, datetime
from zoneinfo import ZoneInfo

from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods
from google.cloud import firestore

from .authentication import require_firebase_auth
from .registration_permissions import require_registration_completed

logger = logging.getLogger(__name__)
JST = ZoneInfo("Asia/Tokyo")


@csrf_exempt
@require_http_methods(["PUT", "DELETE"])
@require_firebase_auth
@require_registration_completed
def post_like(request, post_id):
    """
    PUT: 投稿へのいいねを追加
    DELETE: 自分のいいねを解除
    """

    uid = request.firebase_user["uid"]
    is_like = request.method == "PUT"

    if not post_id or "/" in post_id:
        return JsonResponse(
            {"error": "Invalid post ID"},
            status=400,
        )

    try:
        db = firestore.Client()

        post_ref = db.collection("posts").document(post_id)
        like_ref = post_ref.collection("likes").document(uid)

        @firestore.transactional
        def update_like(transaction):
            # 読み取りを先に完了する
            post_snap = post_ref.get(transaction=transaction)

            if not post_snap.exists:
                return "not_found", None, None

            post = post_snap.to_dict() or {}
            question_id = post.get("questionId")

            if not isinstance(question_id, str) or not question_id:
                return "invalid_post", None, None

            question_ref = (
                db.collection("questions").document(question_id)
            )

            question_snap = question_ref.get(
                transaction=transaction
            )

            if not question_snap.exists:
                return "question_not_found", None, None

            question = question_snap.to_dict() or {}

            # 日本時間で公開期間を検証
            today = datetime.now(JST).date()

            try:
                publish_date = date.fromisoformat(
                    question["publishDate"]
                )
                archive_until = date.fromisoformat(
                    question["archiveUntil"]
                )
            except (KeyError, ValueError, TypeError):
                return "invalid_question", None, None

            if publish_date > archive_until:
                return "invalid_question", None, None

            if not publish_date <= today <= archive_until:
                return "closed", None, None

            like_snap = like_ref.get(transaction=transaction)

            current_count = post.get("likeCount", 0)

            if (
                type(current_count) is not int
                or current_count < 0
            ):
                return "invalid_count", None, None

            already_liked = like_snap.exists

            if is_like:
                if already_liked:
                    # 同じユーザーの二重いいねは変更しない
                    return "unchanged", current_count, True

                transaction.create(
                    like_ref,
                    {
                        "createdAt": firestore.SERVER_TIMESTAMP,
                    },
                )

                transaction.update(
                    post_ref,
                    {
                        "likeCount": firestore.Increment(1),
                    },
                )

                return "updated", current_count + 1, True

            else:
                if not already_liked:
                    # いいねしていなければ変更しない
                    return "unchanged", current_count, False

                transaction.delete(like_ref)

                transaction.update(
                    post_ref,
                    {
                        "likeCount": firestore.Increment(-1),
                    },
                )

                return "updated", current_count - 1, False

        result, like_count, liked = update_like(
            db.transaction()
        )

        error_map = {
            "not_found": (404, "Post not found"),
            "invalid_post": (503, "Post data invalid"),
            "question_not_found": (
                503, "Associated question not found"
            ),
            "invalid_question": (
                503, "Question data invalid"
            ),
            "closed": (403, "Like period closed"),
            "invalid_count": (
                503, "Like count data invalid"
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
                "postId": post_id,
                "liked": liked,
                "likeCount": like_count,
            },
            status=200,
        )

    except Exception:
        logger.exception(
            "Failed to update like for post: %s",
            post_id,
        )

        return JsonResponse(
            {"error": "Like service unavailable"},
            status=503,
        )
