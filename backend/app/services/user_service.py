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
from passlib.context import CryptContext

from app.models.user import User
from app.schemas.user import UserCreate


# -----------------------------------------------------------------------------
# Password hashing context
# Using bcrypt with a cost factor appropriate for development.
# Full authentication system (login, JWT) is built in Week 2.
# -----------------------------------------------------------------------------
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(plain_password: str) -> str:
    """
    Hashes a plaintext password using bcrypt.
    The result is stored in the database — the plaintext is never stored.
    """
    return pwd_context.hash(plain_password)


# -----------------------------------------------------------------------------
# User CRUD operations
# -----------------------------------------------------------------------------

def create_user(db: Session, user_data: UserCreate) -> User:
    """
    Creates a new user in the database.

    Steps:
      1. Check if email already exists (return 409 if duplicate).
      2. Hash the password.
      3. Create and commit the User record.

    Args:
        db:        Active database session.
        user_data: Validated UserCreate schema from the request.

    Returns:
        The newly created User ORM object.

    Raises:
        HTTPException 409: If email is already registered.
    """
    # Check for duplicate email
    existing_user = db.query(User).filter(User.email == user_data.email).first()
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
    hashed = hash_password(user_data.password)

    # Create the User record
    new_user = User(
        name=user_data.name,
        email=user_data.email,
        password_hash=hashed,
        # role and is_active use their model defaults (USER, True)
    )

    db.add(new_user)
    db.commit()
    db.refresh(new_user)   # reload from DB to get generated id, created_at etc.

    return new_user


def get_user_by_id(db: Session, user_id: int) -> User:
    """
    Fetches a user by their integer ID.

    Args:
        db:      Active database session.
        user_id: The user's primary key.

    Returns:
        User ORM object.

    Raises:
        HTTPException 404: If no user with that ID exists.
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
    Used internally (e.g. login check in Week 2).
    """
    return db.query(User).filter(User.email == email).first()
