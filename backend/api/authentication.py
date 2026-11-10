
import firebase_admin
from firebase_admin import auth
from django.http import JsonResponse

# Cloud Runのサービスアカウントを利用する
try:
    firebase_admin.get_app()
except ValueError:
    firebase_admin.initialize_app(
        options={"projectId": "devfestgdgocjapan2026zelos"}
    )


def require_firebase_auth(view_func):
    """Firebaseログイン済みユーザーだけ通す"""

    def wrapper(request, *args, **kwargs):
        header = request.headers.get("Authorization", "")

        if not header.startswith("Bearer "):
            return JsonResponse(
                {"error": "Authentication required"},
                status=401
            )

        token = header[7:].strip()

        if not token:
            return JsonResponse(
                {"error": "Authentication required"},
                status=401
            )

        try:
            decoded = auth.verify_id_token(token)
        except (
            auth.InvalidIdTokenError,
            auth.ExpiredIdTokenError,
            auth.RevokedIdTokenError,
            ValueError,
        ):
            return JsonResponse(
                {"error": "Invalid or expired token"},
                status=401
            )

        request.firebase_user = decoded
        return view_func(request, *args, **kwargs)

    return wrapper
