"""
MedPriority — Emergency API Tests
===================================
Tests for emergency SOS activation, deactivation, queries, and RBAC.

All tests use:
  - Fresh SQLite in-memory database per test
  - JWT auth flow
"""

import pytest
from datetime import datetime, timedelta, UTC
from sqlalchemy.orm import Session

from app.models.user import User, UserRole
from app.models.vehicle import Vehicle, VehicleType
from app.models.emergency_session import EmergencySession, EmergencyStatus
from app.core.security import get_password_hash

# ---------------------------------------------------------------------------
# Test data
# ---------------------------------------------------------------------------
USER1_EMAIL = "sos_user1@example.com"
USER1_PASS = "Pass@123"
USER2_EMAIL = "sos_user2@example.com"
USER2_PASS = "Pass@123"
ADMIN_EMAIL = "sos_admin@example.com"
ADMIN_PASS = "Admin@123"

@pytest.fixture
def setup_sos_data(db: Session):
    """Creates users and vehicles for SOS testing."""
    # 1. Users
    user1 = User(
        name="SOS User 1", email=USER1_EMAIL,
        password_hash=get_password_hash(USER1_PASS),
        role=UserRole.USER, is_active=True
    )
    user2 = User(
        name="SOS User 2", email=USER2_EMAIL,
        password_hash=get_password_hash(USER2_PASS),
        role=UserRole.USER, is_active=True
    )
    admin = User(
        name="SOS Admin", email=ADMIN_EMAIL,
        password_hash=get_password_hash(ADMIN_PASS),
        role=UserRole.ADMIN, is_active=True
    )
    db.add_all([user1, user2, admin])
    db.commit()

    # 2. Vehicles
    v1 = Vehicle(
        user_id=user1.id, vehicle_number="DL01AA1111",
        owner_name="User 1", vehicle_type=VehicleType.CAR, vehicle_color="Red"
    )
    v2 = Vehicle(
        user_id=user2.id, vehicle_number="DL02BB2222",
        owner_name="User 2", vehicle_type=VehicleType.BIKE, vehicle_color="Blue"
    )
    db.add_all([v1, v2])
    db.commit()

    return {
        "user1": user1, "v1": v1,
        "user2": user2, "v2": v2,
        "admin": admin
    }

def _login(client, email: str, password: str) -> str:
    resp = client.post("/api/v1/auth/login", data={"username": email, "password": password})
    assert resp.status_code == 200
    return resp.json()["access_token"]

def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}

# ---------------------------------------------------------------------------
# 1. START Emergency
# ---------------------------------------------------------------------------

def test_start_emergency_success(client, setup_sos_data):
    """Valid user starts emergency on their own vehicle."""
    v1 = setup_sos_data["v1"]
    token = _login(client, USER1_EMAIL, USER1_PASS)
    
    resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    
    assert resp.status_code == 201
    data = resp.json()["data"]
    assert data["user_id"] == setup_sos_data["user1"].id
    assert data["vehicle_id"] == v1.id
    assert data["status"] == "ACTIVE"
    assert data["started_at"] is not None
    assert data["expires_at"] is not None

def test_start_emergency_requires_jwt(client, setup_sos_data):
    v1 = setup_sos_data["v1"]
    resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id})
    assert resp.status_code == 401

def test_start_emergency_nonexistent_vehicle(client, setup_sos_data):
    token = _login(client, USER1_EMAIL, USER1_PASS)
    resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": 9999}, headers=_auth(token))
    assert resp.status_code == 404

def test_start_emergency_others_vehicle(client, setup_sos_data):
    """User 1 tries to start SOS on User 2's vehicle -> 403"""
    v2 = setup_sos_data["v2"]
    token = _login(client, USER1_EMAIL, USER1_PASS)
    resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v2.id}, headers=_auth(token))
    assert resp.status_code == 403

def test_start_emergency_multiple_active(client, setup_sos_data):
    """User cannot start a second ACTIVE emergency."""
    v1 = setup_sos_data["v1"]
    token = _login(client, USER1_EMAIL, USER1_PASS)
    
    # First SOS
    client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    # Second SOS
    resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    
    assert resp.status_code == 409
    assert resp.json()["detail"]["error_code"] == "ACTIVE_EMERGENCY_EXISTS"

# ---------------------------------------------------------------------------
# 2. END Emergency
# ---------------------------------------------------------------------------

def test_end_emergency_success(client, setup_sos_data):
    v1 = setup_sos_data["v1"]
    token = _login(client, USER1_EMAIL, USER1_PASS)
    start_resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    sess_id = start_resp.json()["data"]["id"]

    resp = client.post(f"/api/v1/emergencies/{sess_id}/end", headers=_auth(token))
    assert resp.status_code == 200
    assert resp.json()["data"]["status"] == "ENDED"
    assert resp.json()["data"]["ended_at"] is not None

def test_end_emergency_forbidden(client, setup_sos_data):
    v1 = setup_sos_data["v1"]
    token1 = _login(client, USER1_EMAIL, USER1_PASS)
    token2 = _login(client, USER2_EMAIL, USER2_PASS)
    
    start_resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token1))
    sess_id = start_resp.json()["data"]["id"]

    resp = client.post(f"/api/v1/emergencies/{sess_id}/end", headers=_auth(token2))
    assert resp.status_code == 403

def test_end_emergency_already_ended(client, setup_sos_data):
    v1 = setup_sos_data["v1"]
    token = _login(client, USER1_EMAIL, USER1_PASS)
    start_resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    sess_id = start_resp.json()["data"]["id"]

    client.post(f"/api/v1/emergencies/{sess_id}/end", headers=_auth(token))
    # Second end attempt
    resp = client.post(f"/api/v1/emergencies/{sess_id}/end", headers=_auth(token))
    assert resp.status_code == 409
    assert resp.json()["detail"]["error_code"] == "SESSION_NOT_ACTIVE"

# ---------------------------------------------------------------------------
# 3. GET /active and GET /{id}
# ---------------------------------------------------------------------------

def test_get_active_emergency(client, setup_sos_data):
    v1 = setup_sos_data["v1"]
    token = _login(client, USER1_EMAIL, USER1_PASS)
    
    # Initially None
    resp1 = client.get("/api/v1/emergencies/active", headers=_auth(token))
    assert resp1.status_code == 200
    assert resp1.json()["data"] is None

    # Start
    client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))

    # Now Active
    resp2 = client.get("/api/v1/emergencies/active", headers=_auth(token))
    assert resp2.status_code == 200
    assert resp2.json()["data"]["status"] == "ACTIVE"

def test_get_emergency_by_id_forbidden(client, setup_sos_data):
    v1 = setup_sos_data["v1"]
    token1 = _login(client, USER1_EMAIL, USER1_PASS)
    token2 = _login(client, USER2_EMAIL, USER2_PASS)
    
    start_resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token1))
    sess_id = start_resp.json()["data"]["id"]

    resp = client.get(f"/api/v1/emergencies/{sess_id}", headers=_auth(token2))
    assert resp.status_code == 403

# ---------------------------------------------------------------------------
# 4. History
# ---------------------------------------------------------------------------

def test_get_history(client, setup_sos_data):
    v1 = setup_sos_data["v1"]
    token = _login(client, USER1_EMAIL, USER1_PASS)
    
    # create, end
    r1 = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    client.post(f"/api/v1/emergencies/{r1.json()['data']['id']}/end", headers=_auth(token))
    
    # create, end
    r2 = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    client.post(f"/api/v1/emergencies/{r2.json()['data']['id']}/end", headers=_auth(token))

    resp = client.get("/api/v1/emergencies/history", headers=_auth(token))
    assert resp.status_code == 200
    assert resp.json()["count"] == 2

# ---------------------------------------------------------------------------
# 5. Expiration logic (on-query)
# ---------------------------------------------------------------------------

def test_emergency_expiration_on_query(client, setup_sos_data, db):
    """Test that an active emergency is automatically marked EXPIRED if expires_at is in the past."""
    v1 = setup_sos_data["v1"]
    token = _login(client, USER1_EMAIL, USER1_PASS)
    
    start_resp = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    sess_id = start_resp.json()["data"]["id"]

    # Manually tamper with the database to simulate time passing
    sess = db.query(EmergencySession).filter(EmergencySession.id == sess_id).first()
    sess.expires_at = datetime.now(UTC).replace(tzinfo=None) - timedelta(minutes=5)
    db.commit()

    # Querying it should trigger the on-the-fly expiration
    resp = client.get(f"/api/v1/emergencies/{sess_id}", headers=_auth(token))
    assert resp.status_code == 200
    assert resp.json()["status"] == "EXPIRED"

    # Now the user can start a new emergency
    start_resp2 = client.post("/api/v1/emergencies/start", json={"vehicle_id": v1.id}, headers=_auth(token))
    assert start_resp2.status_code == 201
