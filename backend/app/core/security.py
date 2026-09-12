"""
MedPriority — Security & Authentication Utilities
===================================================
Handles password hashing and JWT token generation/verification.
"""

from datetime import datetime, timedelta, UTC
from typing import Any
from passlib.context import CryptContext
from jose import jwt

from app.core.config import settings

# -----------------------------------------------------------------------------
# Password Hashing Setup
# -----------------------------------------------------------------------------
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    Verifies a plaintext password against a stored bcrypt hash.
    """
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    """
    Hashes a plaintext password using bcrypt.
    """
    return pwd_context.hash(password)


# -----------------------------------------------------------------------------
# JWT Token Generation
# -----------------------------------------------------------------------------
def create_access_token(subject: str | Any, expires_delta: timedelta | None = None) -> str:
    """
    Creates a JWT access token.
    
    Args:
        subject: Typically the user's ID, stored in the 'sub' claim.
        expires_delta: Optional custom expiration. Defaults to settings value.
        
    Returns:
        Encoded JWT string.
    """
    if expires_delta:
        expire = datetime.now(UTC) + expires_delta
    else:
        expire = datetime.now(UTC) + timedelta(minutes=settings.JWT_ACCESS_TOKEN_EXPIRE_MINUTES)
    
    to_encode = {"exp": expire, "sub": str(subject)}
    encoded_jwt = jwt.encode(
        to_encode, 
        settings.JWT_SECRET_KEY, 
        algorithm=settings.JWT_ALGORITHM
    )
    return encoded_jwt
