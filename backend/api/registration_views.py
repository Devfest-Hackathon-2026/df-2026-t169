
import logging

from django.http import JsonResponse
from django.views.decorators.http import require_GET

from .authentication import require_firebase_auth
from .profile_views import (
    get_firestore,
    check_email_verification,
)
from .registration_permissions import (
    get_registration_state as resolve_registration_state,
)

logger = logging.getLogger(__name__)


@require_GET
@require_firebase_auth
def get_registration_state(request):
    """
    GET /users/me/registration
    """

    user = request.firebase_user

    verification_error = check_email_verification(user)
    if verification_error is not None:
        return verification_error

    try:
        state, diagnosis_id = resolve_registration_state(
            user["uid"],
            get_firestore(),
        )

        return JsonResponse({
            "registrationState": state,
            "diagnosisRequired": True,
            "verifiedDiagnosisId": diagnosis_id,
        })

    except Exception:
        logger.exception("Registration state lookup failed")

        return JsonResponse(
            {"error": "Registration state unavailable"},
            status=503,
        )
