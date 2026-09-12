"""
MedPriority — Emergency Session Database Model
================================================
Defines the `emergency_sessions` table in MySQL via SQLAlchemy ORM.

Every EmergencySession belongs to:
  - One authenticated User (FK → users.id)
  - One registered Vehicle owned by that User (FK → vehicles.id)

Lifecycle:
  ACTIVE  → session in progress (SOS is live)
  ENDED   → manually ended by the user
  EXPIRED → server detected that expires_at has passed

Security:
  - user_id and vehicle_id are ALWAYS server-side — never from request body.
  - Only the owner can end their own session.
  - Expired sessions are detected on-query (no background scheduler needed today).
"""

import enum
from datetime import datetime
from typing import TYPE_CHECKING, List

from sqlalchemy import (
    DateTime, Enum as SAEnum, ForeignKey, Integer, String
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base

if TYPE_CHECKING:
    from app.models.user import User
    from app.models.vehicle import Vehicle
    from app.models.location import LocationUpdate


class EmergencyStatus(str, enum.Enum):
    """
    Possible states of an EmergencySession.
    str mixin keeps JSON serialization as plain strings.
    """
    ACTIVE  = "ACTIVE"
    ENDED   = "ENDED"
    EXPIRED = "EXPIRED"


class EmergencySession(Base):
    """
    Represents a row in the `emergency_sessions` MySQL table.

    Columns:
        id          Primary key.
        user_id     FK → users.id  — who triggered the SOS.
        vehicle_id  FK → vehicles.id — which vehicle is in distress.
        status      ACTIVE / ENDED / EXPIRED.
        started_at  Server timestamp when SOS was activated.
        ended_at    Server timestamp when user manually ended (nullable).
        expires_at  Server-computed deadline after which session auto-expires.
        created_at  Row creation timestamp (same as started_at in practice).
    """

    __tablename__ = "emergency_sessions"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        index=True,
        autoincrement=True,
        comment="Primary key",
    )

    user_id: Mapped[int] = mapped_column(
        Integer,
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
        comment="FK → users.id — owner of this emergency session",
    )

    vehicle_id: Mapped[int] = mapped_column(
        Integer,
        ForeignKey("vehicles.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
        comment="FK → vehicles.id — vehicle associated with this SOS",
    )

    status: Mapped[EmergencyStatus] = mapped_column(
        SAEnum(EmergencyStatus),
        default=EmergencyStatus.ACTIVE,
        nullable=False,
        index=True,
        comment="Current lifecycle status of the session",
    )

    started_at: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        comment="Server time when SOS was activated",
    )

    ended_at: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        default=None,
        comment="Server time when user ended the session (null if still active)",
    )

    expires_at: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        comment="Server-computed expiry time — session auto-expires after this",
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        nullable=False,
        comment="Row creation timestamp (UTC)",
    )

    # -------------------------------------------------------------------------
    # Relationships
    # -------------------------------------------------------------------------
    user: Mapped["User"] = relationship(
        "User",
        back_populates="emergency_sessions",
    )

    vehicle: Mapped["Vehicle"] = relationship(
        "Vehicle",
        back_populates="emergency_sessions",
    )

    location_updates: Mapped[List["LocationUpdate"]] = relationship(
        "LocationUpdate",
        backref="emergency_session",
        cascade="all, delete-orphan",
    )

    def __repr__(self) -> str:
        return (
            f"<EmergencySession id={self.id} "
            f"user_id={self.user_id} "
            f"status={self.status}>"
        )
