"""
MedPriority — Emergency Service
=================================
Business logic for Emergency SOS session lifecycle.

Architecture rules:
  - Routers call services. No DB queries in routers.
  - user_id is ALWAYS taken from the authenticated JWT (current_user).
  - Vehicle ownership is verified server-side before any session is created.
  - Expiration is enforced on-query (no background scheduler needed today).

Emergency lifecycle:
  start()  → creates ACTIVE session
  end()    → transitions ACTIVE → ENDED
  [query]  → if expires_at < now, transitions ACTIVE → EXPIRED on-the-fly

Audit events emitted:
  EMERGENCY_STARTED
  EMERGENCY_ENDED
  EMERGENCY_EXPIRED
  UNAUTHORIZED_EMERGENCY_ACCESS
"""

import logging
from datetime import datetime, timedelta, UTC

from sqlalchemy.orm import Session
from fastapi import HTTPException, status

from app.models.emergency_session import EmergencySession, EmergencyStatus
from app.models.vehicle import Vehicle
from app.models.user import User, UserRole
from app.schemas.emergency import EmergencySessionCreate
from app.core.config import settings

# Audit log — uses Python's standard logging (no secret data ever logged)
audit_log = logging.getLogger("medpriority.audit")


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _expire_if_needed(session: EmergencySession, db: Session) -> EmergencySession:
    """
    If an ACTIVE session has passed its expires_at, mark it EXPIRED.
    This implements on-query expiration without a background scheduler.
    """
    now = datetime.now(UTC).replace(tzinfo=None)  # stored as naive UTC in DB
    if session.status == EmergencyStatus.ACTIVE and session.expires_at < now:
        session.status = EmergencyStatus.EXPIRED
        db.commit()
        db.refresh(session)
        audit_log.warning(
            "EMERGENCY_EXPIRED | session_id=%s user_id=%s",
            session.id, session.user_id
        )
    return session


def _get_session_or_404(db: Session, session_id: int) -> EmergencySession:
    """Fetch session by ID, raise 404 if not found."""
    sess = db.query(EmergencySession).filter(EmergencySession.id == session_id).first()
    if not sess:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={
                "status": "error",
                "error_code": "EMERGENCY_NOT_FOUND",
                "message": f"No emergency session found with ID {session_id}.",
            },
        )
    return sess


def _assert_ownership(
    session: EmergencySession,
    current_user: User,
    action: str = "access",
) -> None:
    """
    Raise 403 if current_user is not the owner and not an admin.
    Also emits an audit log event for unauthorized access attempts.
    """
    if current_user.role != UserRole.ADMIN and session.user_id != current_user.id:
        audit_log.warning(
            "UNAUTHORIZED_EMERGENCY_ACCESS | action=%s session_id=%s "
            "requester_id=%s owner_id=%s",
            action, session.id, current_user.id, session.user_id
        )
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={
                "status": "error",
                "error_code": "FORBIDDEN",
                "message": "You do not have permission to access this emergency session.",
            },
        )


# ---------------------------------------------------------------------------
# Public service functions
# ---------------------------------------------------------------------------

def start_emergency(
    db: Session,
    data: EmergencySessionCreate,
    current_user: User,
) -> EmergencySession:
    """
    Start a new SOS emergency session.

    Steps:
      1. Verify vehicle exists.
      2. Verify vehicle belongs to the authenticated user.
      3. Check there is no existing ACTIVE session.
      4. Create session with server-controlled fields.

    Raises:
      404 — vehicle not found
      403 — vehicle doesn't belong to user
      409 — user already has an active emergency session
    """
    # 1. Find vehicle
    vehicle = db.query(Vehicle).filter(Vehicle.id == data.vehicle_id).first()
    if not vehicle:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={
                "status": "error",
                "error_code": "VEHICLE_NOT_FOUND",
                "message": f"No vehicle found with ID {data.vehicle_id}.",
            },
        )

    # 2. Verify ownership — user can only use their own vehicle
    if vehicle.user_id != current_user.id:
        audit_log.warning(
            "UNAUTHORIZED_EMERGENCY_ACCESS | action=start vehicle_id=%s "
            "requester_id=%s owner_id=%s",
            vehicle.id, current_user.id, vehicle.user_id
        )
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={
                "status": "error",
                "error_code": "VEHICLE_NOT_OWNED",
                "message": "This vehicle does not belong to your account.",
            },
        )

    # 3. Check for existing ACTIVE session — expire on-the-fly first
    existing = db.query(EmergencySession).filter(
        EmergencySession.user_id == current_user.id,
        EmergencySession.status == EmergencyStatus.ACTIVE,
    ).first()
    if existing:
        existing = _expire_if_needed(existing, db)  # may flip to EXPIRED
        if existing.status == EmergencyStatus.ACTIVE:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail={
                    "status": "error",
                    "error_code": "ACTIVE_EMERGENCY_EXISTS",
                    "message": (
                        "You already have an active emergency session. "
                        "End the current session before starting a new one."
                    ),
                },
            )

    # 4. Create new session — all fields set by server
    now = datetime.now(UTC).replace(tzinfo=None)
    expire_minutes = settings.EMERGENCY_SESSION_EXPIRY_MINUTES
    new_session = EmergencySession(
        user_id=current_user.id,
        vehicle_id=vehicle.id,
        status=EmergencyStatus.ACTIVE,
        started_at=now,
        ended_at=None,
        expires_at=now + timedelta(minutes=expire_minutes),
        created_at=now,
    )

    db.add(new_session)
    db.commit()
    db.refresh(new_session)

    audit_log.info(
        "EMERGENCY_STARTED | session_id=%s user_id=%s vehicle_id=%s expires_at=%s",
        new_session.id, new_session.user_id, new_session.vehicle_id, new_session.expires_at
    )

    return new_session


def end_emergency(
    db: Session,
    session_id: int,
    current_user: User,
) -> EmergencySession:
    """
    End an active emergency session.

    Raises:
      404 — session not found
      403 — not the owner
      409 — session is not active (already ended or expired)
    """
    sess = _get_session_or_404(db, session_id)
    _assert_ownership(sess, current_user, action="end")
    sess = _expire_if_needed(sess, db)

    if sess.status != EmergencyStatus.ACTIVE:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "status": "error",
                "error_code": "SESSION_NOT_ACTIVE",
                "message": f"Emergency session is already {sess.status.value}.",
            },
        )

    sess.status = EmergencyStatus.ENDED
    sess.ended_at = datetime.now(UTC).replace(tzinfo=None)
    db.commit()
    db.refresh(sess)

    audit_log.info(
        "EMERGENCY_ENDED | session_id=%s user_id=%s",
        sess.id, current_user.id
    )

    return sess


def get_active_emergency(db: Session, current_user: User) -> EmergencySession | None:
    """
    Returns the current user's active emergency session, or None.
    Admins get the first active emergency found across all users
    (for monitoring; can be extended to return all later).
    """
    if current_user.role == UserRole.ADMIN:
        query = db.query(EmergencySession).filter(
            EmergencySession.status == EmergencyStatus.ACTIVE
        )
    else:
        query = db.query(EmergencySession).filter(
            EmergencySession.user_id == current_user.id,
            EmergencySession.status == EmergencyStatus.ACTIVE,
        )

    sess = query.first()
    if sess:
        sess = _expire_if_needed(sess, db)
        if sess.status != EmergencyStatus.ACTIVE:
            return None
    return sess


def get_emergency_by_id(
    db: Session,
    session_id: int,
    current_user: User,
) -> EmergencySession:
    """
    Fetch a specific emergency session by ID.
    Enforces ownership (admins bypass).
    """
    sess = _get_session_or_404(db, session_id)
    _assert_ownership(sess, current_user, action="read")
    return _expire_if_needed(sess, db)


def get_emergency_history(
    db: Session,
    current_user: User,
    skip: int = 0,
    limit: int = 20,
) -> list[EmergencySession]:
    """
    Return the emergency session history.
    Users see only their own sessions.
    Admins see all sessions (paginated).
    """
    if current_user.role == UserRole.ADMIN:
        sessions = (
            db.query(EmergencySession)
            .order_by(EmergencySession.created_at.desc())
            .offset(skip)
            .limit(limit)
            .all()
        )
    else:
        sessions = (
            db.query(EmergencySession)
            .filter(EmergencySession.user_id == current_user.id)
            .order_by(EmergencySession.created_at.desc())
            .offset(skip)
            .limit(limit)
            .all()
        )

    # Expire on-the-fly for any active ones in history
    for sess in sessions:
        if sess.status == EmergencyStatus.ACTIVE:
            _expire_if_needed(sess, db)

    return sessions
