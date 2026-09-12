"""
MedPriority — Emergency / SOS API Endpoints
=============================================
All endpoints require JWT authentication.

Endpoints:
    POST /api/v1/emergencies/start         Activate SOS
    POST /api/v1/emergencies/{id}/end      Deactivate SOS
    GET  /api/v1/emergencies/active        Get current active session
    GET  /api/v1/emergencies/{id}          Get session by ID
    GET  /api/v1/emergencies/history       List session history

IMPORTANT NOTE about FastAPI route ordering:
  /active and /history are defined BEFORE /{id} so FastAPI does not
  interpret the literal strings "active" and "history" as integer IDs.
"""

from fastapi import APIRouter, Depends, status, Query
from sqlalchemy.orm import Session

from app.database.connection import get_db
from app.models.user import User
from app.schemas.emergency import (
    EmergencySessionCreate,
    EmergencySessionResponse,
    EmergencyStartedResponse,
    EmergencyEndedResponse,
    EmergencyActiveResponse,
    EmergencyHistoryResponse,
)
from app.services import emergency_service
from app.api import deps

router = APIRouter(
    prefix="/emergencies",
    tags=["Emergency SOS"],
)


# ---------------------------------------------------------------------------
# POST /api/v1/emergencies/start
# ---------------------------------------------------------------------------
@router.post(
    "/start",
    response_model=EmergencyStartedResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Activate SOS emergency session",
)
def start_emergency(
    data: EmergencySessionCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Starts a new emergency session for the authenticated user.
    - user_id is derived from JWT — cannot be spoofed.
    - vehicle must belong to the authenticated user.
    - Returns 409 if user already has an active session.
    """
    sess = emergency_service.start_emergency(db, data, current_user)
    return EmergencyStartedResponse(data=EmergencySessionResponse.model_validate(sess))


# ---------------------------------------------------------------------------
# GET /api/v1/emergencies/active  — MUST be before /{id}
# ---------------------------------------------------------------------------
@router.get(
    "/active",
    response_model=EmergencyActiveResponse,
    summary="Get current active emergency session",
)
def get_active_emergency(
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Returns the authenticated user's currently active emergency session.
    Returns null in `data` if no active session exists.
    Admins see the first active session across all users.
    """
    sess = emergency_service.get_active_emergency(db, current_user)
    return EmergencyActiveResponse(
        data=EmergencySessionResponse.model_validate(sess) if sess else None
    )


# ---------------------------------------------------------------------------
# GET /api/v1/emergencies/history  — MUST be before /{id}
# ---------------------------------------------------------------------------
@router.get(
    "/history",
    response_model=EmergencyHistoryResponse,
    summary="Get emergency session history",
)
def get_emergency_history(
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
    skip: int = Query(default=0, ge=0, description="Number of records to skip"),
    limit: int = Query(default=20, ge=1, le=100, description="Max records to return"),
):
    """
    Returns the authenticated user's emergency session history.
    Admins see all sessions (paginated).
    Normal users see only their own.
    """
    sessions = emergency_service.get_emergency_history(db, current_user, skip=skip, limit=limit)
    return EmergencyHistoryResponse(
        count=len(sessions),
        data=[EmergencySessionResponse.model_validate(s) for s in sessions],
    )


# ---------------------------------------------------------------------------
# GET /api/v1/emergencies/{id}
# ---------------------------------------------------------------------------
@router.get(
    "/{session_id}",
    response_model=EmergencySessionResponse,
    summary="Get emergency session by ID",
)
def get_emergency(
    session_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Returns a specific emergency session by ID.
    - 404 if not found.
    - 403 if it belongs to a different user (admins bypass).
    """
    return emergency_service.get_emergency_by_id(db, session_id, current_user)


# ---------------------------------------------------------------------------
# POST /api/v1/emergencies/{id}/end
# ---------------------------------------------------------------------------
@router.post(
    "/{session_id}/end",
    response_model=EmergencyEndedResponse,
    summary="End an active emergency session",
)
def end_emergency(
    session_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Ends an active emergency session.
    - 404 if session not found.
    - 403 if not the owner.
    - 409 if already ended or expired.
    """
    sess = emergency_service.end_emergency(db, session_id, current_user)
    return EmergencyEndedResponse(data=EmergencySessionResponse.model_validate(sess))
