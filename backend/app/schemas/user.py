"""
MedPriority — User Pydantic Schemas
=====================================
Pydantic schemas define:
  - What fields are accepted in API requests (input validation)
  - What fields are returned in API responses (output filtering)

SECURITY RULE: password_hash is NEVER included in any response schema.
The 'password' field in UserCreate is the raw input — it is hashed in the
service layer before being stored. It is never stored or returned as-is.
"""

from datetime import datetime
from pydantic import BaseModel, EmailStr, Field, ConfigDict
from app.models.user import UserRole


# -----------------------------------------------------------------------------
# Request Schemas — what the API accepts
# -----------------------------------------------------------------------------

class UserCreate(BaseModel):
    """
    Schema for POST /api/v1/users — creating a new user.

    Fields:
        name:     Full name (2–120 characters).
        email:    Valid email address — must be unique.
        password: Raw password (8–72 chars). Hashed before storage.
    """
    name: str = Field(
        ...,
        min_length=2,
        max_length=120,
        examples=["Arsh Sharma"],
    )
    email: EmailStr = Field(
        ...,
        examples=["arsh@example.com"],
    )
    password: str = Field(
        ...,
        min_length=8,
        max_length=72,   # bcrypt max input length
        examples=["SecurePass@123"],
    )


# -----------------------------------------------------------------------------
# Response Schemas — what the API returns
# -----------------------------------------------------------------------------

class UserResponse(BaseModel):
    """
    Schema for API responses containing user data.

    IMPORTANT: password_hash is intentionally NOT included here.
    No matter what the database contains, the API will never return it.
    """
    id: int
    name: str
    email: str
    role: UserRole
    is_active: bool
    created_at: datetime

    # from_attributes=True allows Pydantic to read from SQLAlchemy model objects
    model_config = ConfigDict(from_attributes=True)


class UserCreatedResponse(BaseModel):
    """
    Response returned after successfully creating a user.
    Wraps UserResponse in a standard envelope.
    """
    status: str = "success"
    message: str = "User created successfully"
    data: UserResponse


class UserGetResponse(BaseModel):
    """
    Response returned when fetching a single user.
    """
    status: str = "success"
    data: UserResponse
