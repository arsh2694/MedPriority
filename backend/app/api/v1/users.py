"""
MedPriority — User API Endpoints
==================================
Endpoints:
    POST   /api/v1/users              Create a new user
    GET    /api/v1/users/{user_id}    Get a user by ID

Note: Authentication (login, JWT) is implemented in Week 2.
      These endpoints currently have no auth guard — that is intentional for Day 3.
"""

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.database.connection import get_db
from app.schemas.user import UserCreate, UserCreatedResponse, UserGetResponse
from app.services import user_service

router = APIRouter(
    prefix="/users",
    tags=["Users"],
)


@router.post(
    "/",
    response_model=UserCreatedResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a new user",
    description=(
        "Registers a new MedPriority user. "
        "The password is hashed before storage. "
        "password_hash is never returned in the response."
    ),
)
def create_user(
    user_data: UserCreate,
    db: Session = Depends(get_db),
):
    """
    Create a new user account.

    - Validates name, email (format), and password (min 8 chars).
    - Returns 409 if email already registered.
    - Stores bcrypt hash — never the plaintext password.
    - Never returns password_hash.
    """
    user = user_service.create_user(db=db, user_data=user_data)
    return UserCreatedResponse(
        status="success",
        message="User created successfully",
        data=user,
    )


@router.get(
    "/{user_id}",
    response_model=UserGetResponse,
    status_code=status.HTTP_200_OK,
    summary="Get user by ID",
    description="Returns safe user information. Never returns password_hash.",
)
def get_user(
    user_id: int,
    db: Session = Depends(get_db),
):
    """
    Retrieve a user by their integer ID.

    - Returns 404 if no user with that ID exists.
    - Never returns password_hash.
    """
    user = user_service.get_user_by_id(db=db, user_id=user_id)
    return UserGetResponse(
        status="success",
        data=user,
    )
