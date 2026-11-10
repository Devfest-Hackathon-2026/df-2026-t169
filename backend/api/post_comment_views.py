
import json
import logging
from datetime import date, datetime
from zoneinfo import ZoneInfo

from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods
from google.cloud import firestore

from .authentication import require_firebase_auth
from .registration_permissions import require_registration_completed
from .diagnosis_questions import TYPE_MAP

logger = logging.getLogger(__name__)
JST = ZoneInfo("Asia/Tokyo")


def _check_comment_period(db, post_id, transaction=None):
    """投稿と関連する問いの存在・公開期間を確認する。"""

    post_ref = db.collection("posts").document(post_id)
    post_snap = post_ref.get(transaction=transaction)

    if not post_snap.exists:
        return "post_not_found", None

    post = post_snap.to_dict() or {}
    question_id = post.get("questionId")

    if not isinstance(question_id, str) or not question_id:
        return "invalid_post", None

    question_ref = db.collection("questions").document(
        question_id
    )
    question_snap = question_ref.get(transaction=transaction)

    if not question_snap.exists:
        return "question_not_found", None

    question = question_snap.to_dict() or {}

    try:
        publish_date = date.fromisoformat(
            question["publishDate"]
        )
        archive_until = date.fromisoformat(
            question["archiveUntil"]
        )
    except (KeyError, ValueError, TypeError):
        return "invalid_question", None

    if (
        publish_date.isoformat() != question_id
        or archive_until < publish_date
    ):
        return "invalid_question", None

    today = datetime.now(JST).date()

    if not publish_date <= today <= archive_until:
        return "closed", None

    return "ok", post


@csrf_exempt
@require_http_methods(["GET", "POST"])
@require_firebase_auth
@require_registration_completed
def post_comments(request, post_id):
    """投稿のコメント一覧取得・新規投稿。"""

    if not post_id or "/" in post_id:
        return JsonResponse(
            {"error": "Invalid post ID"},
            status=400,
        )

    uid = request.firebase_user["uid"]

    content = None

    if request.method == "POST":
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

        content = payload.get("content")

        if not isinstance(content, str):
            return JsonResponse(
                {"error": "Invalid content"},
                status=400,
            )

        content = content.strip()

        if (
            not 1 <= len(content) <= 500
            or any(
                (ord(ch) < 32 and ch not in "\n\r\t")
                or ord(ch) == 127
                for ch in content
            )
        ):
            return JsonResponse(
                {"error": "Invalid comment (1-500 characters)"},
                status=400,
            )

    try:
        db = firestore.Client()

        post_ref = db.collection("posts").document(post_id)
        comments_ref = post_ref.collection("comments")

        if request.method == "POST":
            comment_ref = comments_ref.document()
            user_ref = db.collection("users").document(uid)

            @firestore.transactional
            def save_comment(transaction):
                # 読み取りをすべて先に行う
                status, _ = _check_comment_period(
                    db, post_id, transaction
                )

                if status != "ok":
                    return status, None

                user_snap = user_ref.get(
                    transaction=transaction
                )

                if not user_snap.exists:
                    return "user_not_found", None

                user = user_snap.to_dict() or {}
                latest = user.get("latestDiagnosis") or {}

                type_code = (
                    latest.get("typeCode")
                    if isinstance(latest, dict)
                    else None
                )

                if type_code not in TYPE_MAP:
                    return "diagnosis_unavailable", None

                transaction.create(
                    comment_ref,
                    {
                        "uid": uid,
                        "typeCodeAtComment": type_code,
                        "content": content,
                        "createdAt": firestore.SERVER_TIMESTAMP,
                    },
                )

                return "created", type_code

            status, type_code = save_comment(
                db.transaction()
            )

            if status == "created":
                return JsonResponse(
                    {
                        "id": comment_ref.id,
                        "postId": post_id,
                        "content": content,
                        "typeCodeAtComment": type_code,
                    },
                    status=201,
                    json_dumps_params={"ensure_ascii": False},
                )

        else:
            status, _ = _check_comment_period(
                db, post_id
            )

            if status == "ok":
                snapshots = list(
                    comments_ref.order_by(
                        "createdAt",
                        direction=firestore.Query.DESCENDING,
                    ).limit(50).stream()
                )

                uids = {
                    data.get("uid")
                    for snap in snapshots
                    for data in [snap.to_dict() or {}]
                    if isinstance(data.get("uid"), str)
                    and data.get("uid")
                }

                user_refs = [
                    db.collection("users").document(user_id)
                    for user_id in uids
                ]

                users = {}

                if user_refs:
                    for snap in db.get_all(user_refs):
                        if snap.exists:
                            users[snap.id] = snap.to_dict() or {}

                comments = []

                for snap in snapshots:
                    data = snap.to_dict() or {}
                    author_uid = data.get("uid", "")
                    author = users.get(author_uid, {})

                    type_code = data.get(
                        "typeCodeAtComment", ""
                    )

                    type_info = TYPE_MAP.get(type_code, {})
                    created_at = data.get("createdAt")

                    comments.append({
                        "id": snap.id,
                        "postId": post_id,
                        "userId": author_uid,
                        "userName": author.get(
                            "name", "退会済みユーザー"
                        ),
                        "typeCodeAtComment": type_code,
                        "typeName": type_info.get("name", ""),
                        "content": data.get("content", ""),
                        "createdAt": (
                            created_at.isoformat()
                            if created_at else None
                        ),
                        "own": author_uid == uid,
                    })

                return JsonResponse(
                    {
                        "postId": post_id,
                        "comments": comments,
                    },
                    status=200,
                    json_dumps_params={"ensure_ascii": False},
                )

        error_map = {
            "post_not_found": (404, "Post not found"),
            "invalid_post": (503, "Post data invalid"),
            "question_not_found": (
                503, "Associated question not found"
            ),
            "invalid_question": (
                503, "Question data invalid"
            ),
            "closed": (403, "Comment period closed"),
            "user_not_found": (404, "User not found"),
            "diagnosis_unavailable": (
                409, "Diagnosis unavailable"
            ),
        }

        http_status, message = error_map.get(
            status, (503, "Comment operation failed")
        )

        return JsonResponse(
            {"error": message},
            status=http_status,
        )

    except Exception:
        logger.exception(
            "Comment operation failed: %s",
            post_id,
        )
        return JsonResponse(
            {"error": "Comment service unavailable"},
            status=503,
        )
