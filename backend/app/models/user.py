"""
MedPriority — User Database Model
===================================
Defines the `users` table in MySQL via SQLAlchemy ORM.

Important security rules:
  - password_hash stores only bcrypt hashes, NEVER plaintext passwords.
  - password_hash is NEVER returned in API responses (enforced in schemas).
  - email is unique — no two users can share the same email.
"""

import enum
from datetime import datetime

from sqlalchemy import String, Boolean, DateTime, Enum as SAEnum
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class UserRole(str, enum.Enum):
    """
    Available roles in MedPriority.
    - USER:  Standard user — can register vehicle, trigger SOS, view map.
    - ADMIN: Administrator — can view audit logs and manage users.

    str mixin makes JSON serialization return "USER" not "UserRole.USER".
    """
    USER = "USER"
    ADMIN = "ADMIN"


class User(Base):
    """
    Represents a row in the `users` MySQL table.

    Columns:
        id            Primary key, auto-incremented integer.
        name          Full name of the user.
        email         Unique email address — used for login.
        password_hash Bcrypt hash of the user's password. NEVER plaintext.
        role          UserRole enum — defaults to USER.
        is_active     Soft delete / account status flag. Defaults to True.
        created_at    Timestamp when the account was created (UTC).
    """

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(
        primary_key=True,
        index=True,
        autoincrement=True,
        comment="Primary key",
    )

    name: Mapped[str] = mapped_column(
        String(120),
        nullable=False,
        comment="Full name of the user",
    )

    email: Mapped[str] = mapped_column(
        String(255),
        unique=True,
        nullable=False,
        index=True,
        comment="Unique email address — used for login",
    )

    password_hash: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        comment="Bcrypt hash of the password — NEVER store plaintext",
    )

    role: Mapped[UserRole] = mapped_column(
        SAEnum(UserRole),
        default=UserRole.USER,
        nullable=False,
        comment="User role — USER or ADMIN",
    )

    is_active: Mapped[bool] = mapped_column(
        Boolean,
        default=True,
        nullable=False,
        comment="Account active status — False = soft deleted/disabled",
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        nullable=False,
        comment="Account creation timestamp (UTC)",
    )

    def __repr__(self) -> str:
        return f"<User id={self.id} email={self.email} role={self.role}>"
