"""
MedPriority — Auth Pydantic Schemas
=====================================
Schemas for login requests and JWT token responses.
"""

from pydantic import BaseModel


class Token(BaseModel):
    """
    Standard OAuth2 response schema for returning access tokens.
    """
    access_token: str
    token_type: str = "bearer"


class TokenPayload(BaseModel):
    """
    Schema for decoding the payload inside the JWT.
    """
    sub: str | None = None
