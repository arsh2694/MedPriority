import pytest
from datetime import datetime, UTC
from sqlalchemy.orm import Session
from app.models.user import User, UserRole
from app.models.vehicle import Vehicle, VehicleType
from app.core.security import get_password_hash

# Basic setup similar to test_emergencies
USER1_EMAIL = "loc_user1@example.com"
USER1_PASS = "Pass@123"

@pytest.fixture
def setup_loc_data(db: Session):
    user1 = User(
        name="Loc User", email=USER1_EMAIL,
        password_hash=get_password_hash(USER1_PASS),
        role=UserRole.USER, is_active=True
    )
    db.add(user1)
    db.commit()
    v1 = Vehicle(
        user_id=user1.id, vehicle_number="LOC1111",
        owner_name="User 1", vehicle_type=VehicleType.CAR, vehicle_color="Red"
    )
    db.add(v1)
    db.commit()
    return {"user1": user1, "v1": v1}

def _login(client, email: str, password: str) -> str:
    resp = client.post("/api/v1/auth/login", data={"username": email, "password": password})
    assert resp.status_code == 200
    return resp.json()["access_token"]

def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}

def test_record_location_success(client, setup_loc_data):
    token = _login(client, USER1_EMAIL, USER1_PASS)
    v1 = setup_loc_data["v1"]
    
    # Start SOS
    r1 = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    sess_id = r1.json()["data"]["id"]
    
    # Send location
    loc_data = {
        "latitude": 40.7128,
        "longitude": -74.0060,
        "accuracy": 5.0,
        "recorded_at": datetime.now(UTC).isoformat()
    }
    r2 = client.post(f"/api/v1/emergencies/{sess_id}/location", json=loc_data, headers=_auth(token))
    assert r2.status_code == 201
    data = r2.json()
    assert data["latitude"] == 40.7128
    assert data["longitude"] == -74.0060

def test_record_location_inactive_fails(client, setup_loc_data):
    token = _login(client, USER1_EMAIL, USER1_PASS)
    v1 = setup_loc_data["v1"]
    
    # Start and End SOS
    r1 = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    sess_id = r1.json()["data"]["id"]
    client.post(f"/api/v1/emergencies/{sess_id}/end", headers=_auth(token))
    
    # Send location
    loc_data = {
        "latitude": 40.7128,
        "longitude": -74.0060,
        "accuracy": 5.0,
        "recorded_at": datetime.now(UTC).isoformat()
    }
    r2 = client.post(f"/api/v1/emergencies/{sess_id}/location", json=loc_data, headers=_auth(token))
    assert r2.status_code == 409
