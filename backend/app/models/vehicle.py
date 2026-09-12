"""
MedPriority — Vehicle Database Model
======================================
Defines the `vehicles` table in MySQL via SQLAlchemy ORM.

A Vehicle belongs to exactly one User (Foreign Key → users.id).
One User can own many Vehicles (One-to-Many relationship).

Security:
  - user_id is ALWAYS derived from the authenticated JWT — never from request body.
  - vehicle_number is unique across all users in the system.
"""

import enum
from datetime import datetime
from typing import TYPE_CHECKING, List

from sqlalchemy import String, Boolean, DateTime, Enum as SAEnum, Integer, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base

if TYPE_CHECKING:
    from app.models.user import User
    from app.models.emergency_session import EmergencySession


class VehicleType(str, enum.Enum):
    """
    Supported vehicle types.
    str mixin ensures JSON serialization returns "CAR", not "VehicleType.CAR".
    """
    CAR  = "CAR"
    BIKE = "BIKE"
    SUV  = "SUV"
    VAN  = "VAN"
    TRUCK = "TRUCK"
    OTHER = "OTHER"


class Vehicle(Base):
    """
    Represents a row in the `vehicles` MySQL table.

    Columns:
        id              Primary key, auto-incremented integer.
        user_id         Foreign Key → users.id — the owner of this vehicle.
        vehicle_number  Unique registration number (e.g. DL01AB1234).
        owner_name      Name printed on the vehicle registration.
        vehicle_type    VehicleType enum — CAR, BIKE, SUV, etc.
        vehicle_color   Color of the vehicle (e.g. "Red").
        is_verified     Whether the vehicle is verified by an admin. Default False.
        created_at      Timestamp of registration (UTC).
    """

    __tablename__ = "vehicles"

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
        comment="FK → users.id — owner of this vehicle",
    )

    vehicle_number: Mapped[str] = mapped_column(
        String(20),
        unique=True,
        nullable=False,
        index=True,
        comment="Unique vehicle registration number e.g. DL01AB1234",
    )

    owner_name: Mapped[str] = mapped_column(
        String(120),
        nullable=False,
        comment="Name of the vehicle owner as on registration",
    )

    vehicle_type: Mapped[VehicleType] = mapped_column(
        SAEnum(VehicleType),
        default=VehicleType.CAR,
        nullable=False,
        comment="Type of vehicle: CAR, BIKE, SUV, VAN, TRUCK, OTHER",
    )

    vehicle_color: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="Color of the vehicle",
    )

    is_verified: Mapped[bool] = mapped_column(
        Boolean,
        default=False,
        nullable=False,
        comment="Whether vehicle has been verified by an admin",
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        nullable=False,
        comment="Timestamp when vehicle was registered (UTC)",
    )

    # -------------------------------------------------------------------------
    # Relationships
    # -------------------------------------------------------------------------
    # back_populates connects to User.vehicles — SQLAlchemy manages the join
    owner: Mapped["User"] = relationship(  # type: ignore[name-defined]
        "User",
        back_populates="vehicles",
    )

    emergency_sessions: Mapped[List["EmergencySession"]] = relationship(
        "EmergencySession",
        back_populates="vehicle",
        cascade="all, delete-orphan",
    )

    def __repr__(self) -> str:
        return f"<Vehicle id={self.id} number={self.vehicle_number} user_id={self.user_id}>"
