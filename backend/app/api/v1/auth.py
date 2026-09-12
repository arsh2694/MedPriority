"""
MedPriority — Auth API Endpoints
==================================
Endpoints:
    POST /api/v1/auth/login        Authenticate user and return JWT
    GET  /api/v1/auth/me           Get currently authenticated user profile
    GET  /api/v1/auth/admin-test   Test endpoint for RBAC (Admin only)
"""

from datetime import timedelta
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import create_access_token
from app.database.connection import get_db
from app.models.user import User
from app.schemas.auth import Token
from app.schemas.user import UserResponse
from app.services import user_service
from app.api import deps

router = APIRouter(
    prefix="/auth",
    tags=["Authentication"],
)


@router.post(
    "/login", 
    response_model=Token,
    summary="Login to get access token"
)
def login_access_token(
    db: Session = Depends(get_db),
    form_data: OAuth2PasswordRequestForm = Depends()
) -> dict:
    """
    OAuth2 compatible token login, getting an access token for future requests.
    - Note: Uses 'username' field from form data to represent 'email'.
    """
    user = user_service.authenticate_user(
        db, email=form_data.username, password=form_data.password
    )
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    elif not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Inactive user",
        )
        
    access_token_expires = timedelta(minutes=settings.JWT_ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        subject=user.id, expires_delta=access_token_expires
    )
    
    return {
        "access_token": access_token,
        "token_type": "bearer",
    }


@router.get(
    "/me", 
    response_model=UserResponse,
    summary="Get current user profile"
)
def read_current_user(
    current_user: User = Depends(deps.get_current_active_user)
) -> User:
    """
    Get current user safely.
    Requires a valid JWT Bearer token.
    Never exposes password_hash.
    """
    return current_user


@router.get(
    "/admin-test",
    summary="Test admin role access",
    dependencies=[Depends(deps.get_current_admin_user)]
)
def admin_test_endpoint():
    """
    Protected endpoint only accessible to users with ADMIN role.
    """
    return {
        "status": "success", 
        "message": "You have administrative access."
    }
