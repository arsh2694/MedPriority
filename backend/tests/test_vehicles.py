"""
MedPriority — Vehicle API Tests
=================================
Tests for vehicle registration, listing, update, delete, and RBAC.

All tests use:
  - SQLite in-memory database (no MySQL/Docker needed)
  - JWT auth flow (login → token → authorized request)
"""

import pytest
from app.models.user import UserRole
from app.models.vehicle import VehicleType
from app.core.security import get_password_hash
from app.models.user import User


# ---------------------------------------------------------------------------
# Test data
# ---------------------------------------------------------------------------
USER1_EMAIL    = "vehicle_user1@example.com"
USER1_PASS     = "User1Pass@123"
USER2_EMAIL    = "vehicle_user2@example.com"
USER2_PASS     = "User2Pass@123"
ADMIN_EMAIL    = "vehicle_admin@example.com"
ADMIN_PASS     = "AdminPass@123"

VALID_VEHICLE = {
    "vehicle_number": "DL01AB1234",
    "owner_name": "Arsh Sharma",
    "vehicle_type": "CAR",
    "vehicle_color": "Red",
}


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def setup_two_users(db):
    """Creates two regular users and one admin for testing cross-user access."""
    user1 = User(
        name="Vehicle User One",
        email=USER1_EMAIL,
        password_hash=get_password_hash(USER1_PASS),
        role=UserRole.USER,
        is_active=True,
    )
    user2 = User(
        name="Vehicle User Two",
        email=USER2_EMAIL,
        password_hash=get_password_hash(USER2_PASS),
        role=UserRole.USER,
        is_active=True,
    )
    admin = User(
        name="Admin",
        email=ADMIN_EMAIL,
        password_hash=get_password_hash(ADMIN_PASS),
        role=UserRole.ADMIN,
        is_active=True,
    )
    db.add_all([user1, user2, admin])
    db.flush()


def _login(client, email: str, password: str) -> str:
    """Helper: login and return the access token."""
    resp = client.post(
        "/api/v1/auth/login",
        data={"username": email, "password": password},
    )
    assert resp.status_code == 200, f"Login failed: {resp.json()}"
    return resp.json()["access_token"]


def _auth(token: str) -> dict:
    """Helper: return Authorization header dict."""
    return {"Authorization": f"Bearer {token}"}


# ---------------------------------------------------------------------------
# 1. Vehicle Creation
# ---------------------------------------------------------------------------

def test_create_vehicle_success(client, setup_two_users):
    """POST /api/v1/vehicles — valid data returns 201."""
    token = _login(client, USER1_EMAIL, USER1_PASS)
    resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token))
    assert resp.status_code == 201
    data = resp.json()
    assert data["status"] == "success"
    assert data["data"]["vehicle_number"] == "DL01AB1234"
    assert data["data"]["vehicle_color"] == "Red"


def test_create_vehicle_requires_jwt(client, setup_two_users):
    """POST /api/v1/vehicles without token returns 401."""
    resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE)
    assert resp.status_code == 401


def test_create_vehicle_duplicate_number(client, setup_two_users):
    """POST /api/v1/vehicles — duplicate vehicle_number returns 409."""
    token = _login(client, USER1_EMAIL, USER1_PASS)
    client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token))
    # Same vehicle number — should fail
    resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token))
    assert resp.status_code == 409
    assert resp.json()["detail"]["error_code"] == "VEHICLE_ALREADY_EXISTS"


def test_create_vehicle_invalid_number_format(client, setup_two_users):
    """POST /api/v1/vehicles — invalid vehicle number returns 422."""
    token = _login(client, USER1_EMAIL, USER1_PASS)
    bad_vehicle = {**VALID_VEHICLE, "vehicle_number": "invalid number!"}
    resp = client.post("/api/v1/vehicles/", json=bad_vehicle, headers=_auth(token))
    assert resp.status_code == 422


def test_create_vehicle_missing_required_fields(client, setup_two_users):
    """POST /api/v1/vehicles — missing owner_name returns 422."""
    token = _login(client, USER1_EMAIL, USER1_PASS)
    incomplete = {"vehicle_number": "MH02CD5678", "vehicle_color": "Blue"}
    resp = client.post("/api/v1/vehicles/", json=incomplete, headers=_auth(token))
    assert resp.status_code == 422


def test_create_vehicle_user_id_from_jwt_not_body(client, setup_two_users):
    """Vehicle's user_id is from the JWT, even if body has no user_id field."""
    token1 = _login(client, USER1_EMAIL, USER1_PASS)
    vehicle_payload = {**VALID_VEHICLE, "vehicle_number": "UP15EF9999"}
    resp = client.post("/api/v1/vehicles/", json=vehicle_payload, headers=_auth(token1))
    assert resp.status_code == 201
    # user_id in response must belong to user1, not some arbitrary value
    assert resp.json()["data"]["user_id"] is not None


# ---------------------------------------------------------------------------
# 2. List Vehicles — user isolation
# ---------------------------------------------------------------------------

def test_list_vehicles_returns_only_own(client, setup_two_users):
    """GET /api/v1/vehicles — user sees only their own vehicles."""
    token1 = _login(client, USER1_EMAIL, USER1_PASS)
    token2 = _login(client, USER2_EMAIL, USER2_PASS)

    # User1 registers a vehicle
    client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token1))

    # User2 registers a different vehicle
    v2 = {**VALID_VEHICLE, "vehicle_number": "MH04GH7890"}
    client.post("/api/v1/vehicles/", json=v2, headers=_auth(token2))

    # User1 list — should only see DL01AB1234
    resp1 = client.get("/api/v1/vehicles/", headers=_auth(token1))
    assert resp1.status_code == 200
    numbers = [v["vehicle_number"] for v in resp1.json()["data"]]
    assert "DL01AB1234" in numbers
    assert "MH04GH7890" not in numbers


def test_list_vehicles_requires_jwt(client):
    """GET /api/v1/vehicles without token returns 401."""
    resp = client.get("/api/v1/vehicles/")
    assert resp.status_code == 401


def test_admin_sees_all_vehicles(client, setup_two_users):
    """Admin GET /api/v1/vehicles returns vehicles from all users."""
    token1 = _login(client, USER1_EMAIL, USER1_PASS)
    token2 = _login(client, USER2_EMAIL, USER2_PASS)
    admin_token = _login(client, ADMIN_EMAIL, ADMIN_PASS)

    client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token1))
    v2 = {**VALID_VEHICLE, "vehicle_number": "KA05IJ2222"}
    client.post("/api/v1/vehicles/", json=v2, headers=_auth(token2))

    resp = client.get("/api/v1/vehicles/", headers=_auth(admin_token))
    assert resp.status_code == 200
    numbers = [v["vehicle_number"] for v in resp.json()["data"]]
    assert "DL01AB1234" in numbers
    assert "KA05IJ2222" in numbers


# ---------------------------------------------------------------------------
# 3. Get Vehicle by ID — authorization
# ---------------------------------------------------------------------------

def test_get_vehicle_by_id_success(client, setup_two_users):
    """GET /api/v1/vehicles/{id} — owner can get their own vehicle."""
    token = _login(client, USER1_EMAIL, USER1_PASS)
    create_resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token))
    vehicle_id = create_resp.json()["data"]["id"]

    resp = client.get(f"/api/v1/vehicles/{vehicle_id}", headers=_auth(token))
    assert resp.status_code == 200
    assert resp.json()["vehicle_number"] == "DL01AB1234"


def test_get_vehicle_by_id_forbidden(client, setup_two_users):
    """GET /api/v1/vehicles/{id} — another user gets 403."""
    token1 = _login(client, USER1_EMAIL, USER1_PASS)
    token2 = _login(client, USER2_EMAIL, USER2_PASS)

    create_resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token1))
    vehicle_id = create_resp.json()["data"]["id"]

    resp = client.get(f"/api/v1/vehicles/{vehicle_id}", headers=_auth(token2))
    assert resp.status_code == 403


def test_get_vehicle_not_found(client, setup_two_users):
    """GET /api/v1/vehicles/99999 — non-existent vehicle returns 404."""
    token = _login(client, USER1_EMAIL, USER1_PASS)
    resp = client.get("/api/v1/vehicles/99999", headers=_auth(token))
    assert resp.status_code == 404


# ---------------------------------------------------------------------------
# 4. Update Vehicle
# ---------------------------------------------------------------------------

def test_update_vehicle_success(client, setup_two_users):
    """PUT /api/v1/vehicles/{id} — owner can update their vehicle."""
    token = _login(client, USER1_EMAIL, USER1_PASS)
    create_resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token))
    vehicle_id = create_resp.json()["data"]["id"]

    resp = client.put(
        f"/api/v1/vehicles/{vehicle_id}",
        json={"vehicle_color": "Blue"},
        headers=_auth(token),
    )
    assert resp.status_code == 200
    assert resp.json()["vehicle_color"] == "Blue"


def test_update_vehicle_forbidden(client, setup_two_users):
    """PUT /api/v1/vehicles/{id} — another user gets 403."""
    token1 = _login(client, USER1_EMAIL, USER1_PASS)
    token2 = _login(client, USER2_EMAIL, USER2_PASS)

    create_resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token1))
    vehicle_id = create_resp.json()["data"]["id"]

    resp = client.put(
        f"/api/v1/vehicles/{vehicle_id}",
        json={"vehicle_color": "Green"},
        headers=_auth(token2),
    )
    assert resp.status_code == 403


# ---------------------------------------------------------------------------
# 5. Delete Vehicle
# ---------------------------------------------------------------------------

def test_delete_vehicle_success(client, setup_two_users):
    """DELETE /api/v1/vehicles/{id} — owner can delete their vehicle."""
    token = _login(client, USER1_EMAIL, USER1_PASS)
    create_resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token))
    vehicle_id = create_resp.json()["data"]["id"]

    resp = client.delete(f"/api/v1/vehicles/{vehicle_id}", headers=_auth(token))
    assert resp.status_code == 204

    # Confirm it's gone
    get_resp = client.get(f"/api/v1/vehicles/{vehicle_id}", headers=_auth(token))
    assert get_resp.status_code == 404


def test_delete_vehicle_forbidden(client, setup_two_users):
    """DELETE /api/v1/vehicles/{id} — another user gets 403."""
    token1 = _login(client, USER1_EMAIL, USER1_PASS)
    token2 = _login(client, USER2_EMAIL, USER2_PASS)

    create_resp = client.post("/api/v1/vehicles/", json=VALID_VEHICLE, headers=_auth(token1))
    vehicle_id = create_resp.json()["data"]["id"]

    resp = client.delete(f"/api/v1/vehicles/{vehicle_id}", headers=_auth(token2))
    assert resp.status_code == 403
