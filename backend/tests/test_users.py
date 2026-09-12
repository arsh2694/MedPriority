"""
MedPriority — User API Tests
==============================
Tests for:
  1. User creation (POST /api/v1/users)
  2. Duplicate email rejection
  3. Get user by ID (GET /api/v1/users/{id})
  4. password_hash never returned in responses
  5. User not found (404)
  6. Invalid input handling (422 validation errors)
  7. Database connection (SQLite test DB)
"""

import pytest


# ---------------------------------------------------------------------------
# Test data
# ---------------------------------------------------------------------------
VALID_USER = {
    "name": "Arsh Sharma",
    "email": "arsh@example.com",
    "password": "SecurePass@123",
}


# ---------------------------------------------------------------------------
# 1. Test: Successful user creation
# ---------------------------------------------------------------------------
def test_create_user_success(client):
    """POST /api/v1/users with valid data returns 201 and user info."""
    response = client.post("/api/v1/users/", json=VALID_USER)

    assert response.status_code == 201
    data = response.json()

    assert data["status"] == "success"
    assert data["data"]["email"] == VALID_USER["email"]
    assert data["data"]["name"] == VALID_USER["name"]
    assert data["data"]["role"] == "USER"
    assert data["data"]["is_active"] is True
    assert "id" in data["data"]
    assert "created_at" in data["data"]


# ---------------------------------------------------------------------------
# 2. Test: password_hash NEVER returned in response
# ---------------------------------------------------------------------------
def test_password_hash_not_in_response(client):
    """
    SECURITY TEST: password_hash must never appear in any API response.
    Also verifies 'password' (plaintext) is not returned.
    """
    response = client.post("/api/v1/users/", json={
        "name": "Test User",
        "email": "secure_test@example.com",
        "password": "TestPassword@99",
    })

    assert response.status_code == 201
    response_text = response.text

    # Neither the hash field nor the plaintext password should appear
    assert "password_hash" not in response_text
    assert "TestPassword@99" not in response_text


# ---------------------------------------------------------------------------
# 3. Test: Duplicate email returns 409
# ---------------------------------------------------------------------------
def test_create_user_duplicate_email(client):
    """POST /api/v1/users with an already-registered email returns 409."""
    # First creation — should succeed
    client.post("/api/v1/users/", json={
        "name": "First User",
        "email": "duplicate@example.com",
        "password": "Password@123",
    })

    # Second creation with same email — should fail
    response = client.post("/api/v1/users/", json={
        "name": "Second User",
        "email": "duplicate@example.com",
        "password": "OtherPassword@123",
    })

    assert response.status_code == 409
    data = response.json()
    assert data["detail"]["error_code"] == "EMAIL_ALREADY_EXISTS"


# ---------------------------------------------------------------------------
# 4. Test: Get user by ID
# ---------------------------------------------------------------------------
def test_get_user_by_id(client):
    """GET /api/v1/users/{id} returns the correct user."""
    # Create a user first
    create_response = client.post("/api/v1/users/", json={
        "name": "Get Test User",
        "email": "gettest@example.com",
        "password": "Password@123",
    })
    user_id = create_response.json()["data"]["id"]

    # Fetch by ID
    response = client.get(f"/api/v1/users/{user_id}")

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["data"]["id"] == user_id
    assert data["data"]["email"] == "gettest@example.com"
    assert "password_hash" not in response.text


# ---------------------------------------------------------------------------
# 5. Test: Get non-existent user returns 404
# ---------------------------------------------------------------------------
def test_get_user_not_found(client):
    """GET /api/v1/users/99999 returns 404 when user does not exist."""
    response = client.get("/api/v1/users/99999")

    assert response.status_code == 404
    data = response.json()
    assert data["detail"]["error_code"] == "USER_NOT_FOUND"


# ---------------------------------------------------------------------------
# 6. Test: Invalid input — missing fields
# ---------------------------------------------------------------------------
def test_create_user_missing_fields(client):
    """POST /api/v1/users with missing required fields returns 422."""
    response = client.post("/api/v1/users/", json={
        "name": "Incomplete User",
        # email and password missing
    })
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# 7. Test: Invalid input — password too short
# ---------------------------------------------------------------------------
def test_create_user_short_password(client):
    """POST /api/v1/users with password < 8 chars returns 422."""
    response = client.post("/api/v1/users/", json={
        "name": "Short Pass User",
        "email": "shortpass@example.com",
        "password": "abc",   # too short
    })
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# 8. Test: Invalid email format
# ---------------------------------------------------------------------------
def test_create_user_invalid_email(client):
    """POST /api/v1/users with invalid email format returns 422."""
    response = client.post("/api/v1/users/", json={
        "name": "Bad Email User",
        "email": "not-an-email",
        "password": "ValidPass@123",
    })
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# 9. Test: Database connection (via health endpoint)
# ---------------------------------------------------------------------------
def test_health_endpoint_api_ok(client):
    """GET /health returns api: ok (database may show not_configured in test)."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["api"] == "ok"
