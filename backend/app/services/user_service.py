"""
MedPriority — User Service
===========================
Business logic for user operations.

The API router calls these functions.
This layer handles:
  - Duplicate email checking
  - Password hashing (temporary bcrypt — full auth in Week 2)
  - Database queries

Architecture rule: No database queries in routers. Routers call services.
"""

from sqlalchemy.orm import Session
from fastapi import HTTPException, status

from app.models.user import User
from app.schemas.user import UserCreate
from app.core.security import get_password_hash, verify_password


# -----------------------------------------------------------------------------
# User Authentication & CRUD operations
# -----------------------------------------------------------------------------

def authenticate_user(db: Session, email: str, password: str) -> User | None:
    """
    Verifies a user's email and password.
    Returns the User object if successful, None otherwise.
    """
    user = get_user_by_email(db, email)
    if not user:
        return None
    if not verify_password(password, user.password_hash):
        return None
    return user


def create_user(db: Session, user_data: UserCreate) -> User:
    """
    Creates a new user in the database.

    Steps:
      1. Check if email already exists (return 409 if duplicate).
      2. Hash the password.
      3. Create and commit the User record.
    """
    existing_user = get_user_by_email(db, user_data.email)
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "status": "error",
                "error_code": "EMAIL_ALREADY_EXISTS",
                "message": "An account with this email address already exists.",
            },
        )

    # Hash the password before storing
    hashed = get_password_hash(user_data.password)

    new_user = User(
        name=user_data.name,
        email=user_data.email,
        password_hash=hashed,
    )

    db.add(new_user)
    db.commit()
    db.refresh(new_user)

    return new_user


def get_user_by_id(db: Session, user_id: int) -> User:
    """
    Fetches a user by their integer ID. Returns 404 if not found.
    """
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={
                "status": "error",
                "error_code": "USER_NOT_FOUND",
                "message": f"No user found with ID {user_id}.",
            },
        )
    return user


def get_user_by_email(db: Session, email: str) -> User | None:
    """
    Fetches a user by email. Returns None if not found.
    """
    return db.query(User).filter(User.email == email).first()
