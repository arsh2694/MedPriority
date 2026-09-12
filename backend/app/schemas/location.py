from pydantic import BaseModel, Field
from datetime import datetime
from typing import Optional

class LocationCreate(BaseModel):
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    accuracy: Optional[float] = Field(None, ge=0.0)
    recorded_at: datetime = Field(default_factory=datetime.utcnow)

class LocationResponse(BaseModel):
    id: int
    session_id: int
    latitude: float
    longitude: float
    accuracy: Optional[float]
    recorded_at: datetime
    created_at: datetime

    class Config:
        from_attributes = True
