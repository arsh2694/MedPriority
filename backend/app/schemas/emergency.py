"""
MedPriority — Emergency Session Pydantic Schemas
==================================================
Defines request and response shapes for Emergency/SOS API endpoints.

Security rules:
  - Client CANNOT set: user_id, status, started_at, ended_at, expires_at, created_at
  - All server-controlled fields are set exclusively in the service layer.
  - Client only provides: vehicle_id (which vehicle is in distress).
"""

from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict
from app.models.emergency_session import EmergencyStatus


# ---------------------------------------------------------------------------
# Request Schemas
# ---------------------------------------------------------------------------

class EmergencySessionCreate(BaseModel):
    """
    Schema for POST /api/v1/emergencies/start.
    Client only provides the vehicle_id — everything else is set by the server.
    """
    vehicle_id: int = Field(
        ...,
        gt=0,
        examples=[1],
        description="ID of the vehicle that is in distress (must belong to authenticated user)",
    )


# ---------------------------------------------------------------------------
# Response Schemas
# ---------------------------------------------------------------------------

class EmergencySessionResponse(BaseModel):
    """
    Safe emergency session response returned to the client.
    """
    id: int
    user_id: int
    vehicle_id: int
    status: EmergencyStatus
    started_at: datetime
    ended_at: datetime | None
    expires_at: datetime
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class EmergencyStartedResponse(BaseModel):
    """Envelope returned after successfully starting an emergency session."""
    status: str = "success"
    message: str = "Emergency session started. Help is on the way."
    data: EmergencySessionResponse


class EmergencyEndedResponse(BaseModel):
    """Envelope returned after ending an emergency session."""
    status: str = "success"
    message: str = "Emergency session ended."
    data: EmergencySessionResponse


class EmergencyActiveResponse(BaseModel):
    """Envelope for active session query — may be null if no active session."""
    status: str = "success"
    data: EmergencySessionResponse | None


class EmergencyHistoryResponse(BaseModel):
    """Envelope for listing emergency history."""
    status: str = "success"
    count: int
    data: list[EmergencySessionResponse]
