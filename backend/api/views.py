

import logging
from datetime import datetime
from zoneinfo import ZoneInfo

from django.http import JsonResponse
from django.views.decorators.http import require_GET
from google.cloud import firestore

from .authentication import require_firebase_auth
from .registration_permissions import require_registration_completed
from .diagnosis_questions import TYPE_MAP

logger = logging.getLogger(__name__)

JST = ZoneInfo("Asia/Tokyo")


def health(request):
    """ヘルスチェック"""
    return JsonResponse({"status": "ok"})


def _format_timestamp(value):
    if value is None:
        return None

    if hasattr(value, "isoformat"):
        return value.isoformat()

    return None


def _enrich_posts(db, documents, viewer_uid):
    """
    投稿にユーザー名・診断タイプ・問い情報を結合する。

    Firestoreには保存されていない表示用情報を
    Django側で生成する。
    """

    post_snapshots = list(documents)

    user_ids = set()
    question_ids = set()

    for doc in post_snapshots:
        data = doc.to_dict() or {}

        uid = data.get("uid")
        question_id = data.get("questionId")

        if isinstance(uid, str) and uid:
            user_ids.add(uid)

        if isinstance(question_id, str) and question_id:
            question_ids.add(question_id)

    # ユーザー情報をまとめて取得
    user_refs = [
        db.collection("users").document(uid)
        for uid in user_ids
    ]

    users = {}

    if user_refs:
        for snapshot in db.get_all(user_refs):
            if snapshot.exists:
                users[snapshot.id] = snapshot.to_dict() or {}

    # 問い情報をまとめて取得
    question_refs = [
        db.collection("questions").document(qid)
        for qid in question_ids
    ]

    questions = {}

    if question_refs:
        for snapshot in db.get_all(question_refs):
            if snapshot.exists:
                questions[snapshot.id] = snapshot.to_dict() or {}

    
    # ログインユーザー自身のいいね状態を一括取得
    like_refs = [
        db.collection("posts")
        .document(doc.id)
        .collection("likes")
        .document(viewer_uid)
        for doc in post_snapshots
    ]

    liked_post_ids = set()

    if like_refs:
        for snapshot in db.get_all(like_refs):
            if snapshot.exists:
                liked_post_ids.add(
                    snapshot.reference.parent.parent.id
                )


    results = []

    for doc in post_snapshots:
        data = doc.to_dict() or {}

        uid = data.get("uid", "")
        question_id = data.get("questionId", "")

        user = users.get(uid, {})
        question = questions.get(question_id, {})

        type_code = data.get("typeCodeAtPost", "")
        type_info = TYPE_MAP.get(type_code, {})

        created_at = _format_timestamp(
            data.get("createdAt")
        )

        results.append({
            "id": doc.id,

            # Androidの既存フィールド名も維持
            "userId": uid,
            "userName": user.get("name", "退会済みユーザー"),

            # 投稿時点の診断タイプ
            "typeCodeAtPost": type_code,
            "typeName": type_info.get("name", ""),

            # 問いとの関連付け
            "questionId": question_id,
            "questionText": question.get("text", ""),
            "questionTag": question.get("tag", ""),
            "publishDate": question.get("publishDate"),
            "archiveUntil": question.get("archiveUntil"),

            # 投稿内容
            "content": data.get("content", ""),
            "selectedOptionIndex": data.get(
                "selectedOptionIndex"
            ),
            "likeCount": data.get("likeCount", 0),
            "createdAt": created_at,
            "isLiked": doc.id in liked_post_ids,
        })

    results.sort(
        key=lambda post: post["createdAt"] or "",
        reverse=True,
    )

    return results


@require_GET
@require_firebase_auth
@require_registration_completed
def gets_all_posts(request):
    """
    公開期限内の問いに紐づく投稿を取得する。
    """

    today = datetime.now(JST).date().isoformat()

    try:
        db = firestore.Client()

        # アーカイブ期限が切れていない問い
        question_docs = (
            db.collection("questions")
            .where("archiveUntil", ">=", today)
            .limit(30)
            .stream()
        )

        active_question_ids = []

        for doc in question_docs:
            data = doc.to_dict() or {}

            publish_date = data.get("publishDate")
            archive_until = data.get("archiveUntil")

            if (
                isinstance(publish_date, str)
                and isinstance(archive_until, str)
                and publish_date <= today <= archive_until
            ):
                active_question_ids.append(doc.id)

        # 公開対象の問いに属する投稿を取得
        documents = []

        for question_id in active_question_ids:
            query = (
                db.collection("posts")
                .where("questionId", "==", question_id)
                .limit(50)
            )

            documents.extend(query.stream())

        results = _enrich_posts(
            db,
            documents,
            request.firebase_user["uid"],
        )

        return JsonResponse(
            {"posts": results[:50]},
            status=200,
            json_dumps_params={"ensure_ascii": False},
        )

    except Exception:
        logger.exception("Failed to retrieve posts")

        return JsonResponse(
            {"error": "Posts unavailable"},
            status=503,
        )


@require_GET
@require_firebase_auth
@require_registration_completed
def my_posts(request):
    """
    自分の投稿を取得する。
    アーカイブ期限後も取得可能。
    """

    uid = request.firebase_user["uid"]

    try:
        db = firestore.Client()

        documents = (
            db.collection("posts")
            .where("uid", "==", uid)
            .limit(100)
            .stream()
        )

        
        results = _enrich_posts(
            db,
            documents,
            uid,
        )

        return JsonResponse(
            {"posts": results},
            status=200,
            json_dumps_params={"ensure_ascii": False},
        )

    except Exception:
        logger.exception("Failed to retrieve own posts")

        return JsonResponse(
            {"error": "User posts unavailable"},
            status=503,
        )


@require_GET
@require_firebase_auth
@require_registration_completed
def get_question_posts(request, question_id):
    """指定した問いに紐づく公開投稿を取得する。"""

    today = datetime.now(JST).date().isoformat()

    try:
        # Firestoreの不正なドキュメントパスを防ぐ
        from datetime import date

        parsed = date.fromisoformat(question_id)

        if parsed.isoformat() != question_id:
            raise ValueError

    except (ValueError, TypeError):
        return JsonResponse(
            {"error": "Invalid questionId"},
            status=400,
        )

    try:
        db = firestore.Client()

        # 問いの存在と公開期限を確認
        question_snapshot = (
            db.collection("questions")
            .document(question_id)
            .get()
        )

        if not question_snapshot.exists:
            return JsonResponse(
                {"error": "Question not found"},
                status=404,
            )

        question = question_snapshot.to_dict() or {}

        publish_date = question.get("publishDate")
        archive_until = question.get("archiveUntil")

        if (
            not isinstance(publish_date, str)
            or not isinstance(archive_until, str)
            or publish_date != question_id
            or not publish_date <= today <= archive_until
        ):
            return JsonResponse(
                {"error": "Question posts unavailable"},
                status=403,
            )

        # 指定された問いの投稿だけを取得
        documents = (
            db.collection("posts")
            .where("questionId", "==", question_id)
            .limit(50)
            .stream()
        )

        # 既存のユーザー情報・タイプ情報結合を再利用
        posts = _enrich_posts(
            db,
            documents,
            request.firebase_user["uid"],
        )

        return JsonResponse(
            {
                "questionId": question_id,
                "posts": posts,
            },
            status=200,
            json_dumps_params={"ensure_ascii": False},
        )

    except Exception:
        logger.exception(
            "Failed to retrieve question posts: %s",
            question_id,
        )

        return JsonResponse(
            {"error": "Question posts unavailable"},
            status=503,
        )
