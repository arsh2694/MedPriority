"""
MedPriority — Vehicle Pydantic Schemas
========================================
Defines request and response shapes for Vehicle API endpoints.

Security:
  - user_id is NEVER accepted from request body (always from JWT).
  - is_verified is NEVER settable by regular users.
"""

import re
from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict, field_validator
from app.models.vehicle import VehicleType


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
VEHICLE_NUMBER_PATTERN = re.compile(r"^[A-Z0-9\-]{3,20}$")


# ---------------------------------------------------------------------------
# Request Schemas
# ---------------------------------------------------------------------------

class VehicleCreate(BaseModel):
    """
    Schema for POST /api/v1/vehicles — creating a new vehicle.

    user_id is derived from the JWT — NOT accepted from the request body.
    """
    vehicle_number: str = Field(
        ...,
        min_length=3,
        max_length=20,
        examples=["DL01AB1234"],
        description="Unique vehicle registration number (uppercase letters, digits, hyphens only)",
    )
    owner_name: str = Field(
        ...,
        min_length=2,
        max_length=120,
        examples=["Arsh Sharma"],
        description="Name of the vehicle owner as on registration certificate",
    )
    vehicle_type: VehicleType = Field(
        default=VehicleType.CAR,
        examples=["CAR"],
        description="Type of vehicle: CAR, BIKE, SUV, VAN, TRUCK, OTHER",
    )
    vehicle_color: str = Field(
        ...,
        min_length=2,
        max_length=50,
        examples=["Red"],
        description="Color of the vehicle",
    )

    @field_validator("vehicle_number")
    @classmethod
    def validate_vehicle_number(cls, v: str) -> str:
        """Force uppercase and validate format."""
        v = v.upper().strip()
        if not VEHICLE_NUMBER_PATTERN.match(v):
            raise ValueError(
                "Vehicle number must be 3–20 characters. "
                "Only uppercase letters, digits, and hyphens are allowed."
            )
        return v


class VehicleUpdate(BaseModel):
    """
    Schema for PUT /api/v1/vehicles/{id} — partial update.
    All fields are optional — only provided fields are updated.
    vehicle_number cannot be changed after creation (same as a real RC).
    """
    owner_name: str | None = Field(None, min_length=2, max_length=120)
    vehicle_type: VehicleType | None = None
    vehicle_color: str | None = Field(None, min_length=2, max_length=50)


# ---------------------------------------------------------------------------
# Response Schemas
# ---------------------------------------------------------------------------

class VehicleResponse(BaseModel):
    """
    Safe vehicle response — returned to the client.
    """
    id: int
    user_id: int
    vehicle_number: str
    owner_name: str
    vehicle_type: VehicleType
    vehicle_color: str
    is_verified: bool
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class VehicleCreatedResponse(BaseModel):
    """Envelope returned after successfully registering a vehicle."""
    status: str = "success"
    message: str = "Vehicle registered successfully"
    data: VehicleResponse


class VehicleListResponse(BaseModel):
    """Envelope returned when listing vehicles."""
    status: str = "success"
    count: int
    data: list[VehicleResponse]
