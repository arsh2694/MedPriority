"""
MedPriority — Auth API Tests
==============================
Tests for:
  1. User authentication (Login)
  2. JWT token validation
  3. Protected endpoints (/me)
  4. RBAC Role checking (/admin-test)
"""

import pytest
from app.models.user import UserRole
from app.services import user_service
from app.core.security import get_password_hash

# ---------------------------------------------------------------------------
# Test data
# ---------------------------------------------------------------------------
USER_EMAIL = "authuser@example.com"
USER_PASS = "AuthPass@123"

ADMIN_EMAIL = "admin@example.com"
ADMIN_PASS = "AdminPass@123"

@pytest.fixture
def setup_users(db):
    """
    Fixture to create test users directly in the database.
    """
    # Create normal user
    user = user_service.User(
        name="Auth Test User",
        email=USER_EMAIL,
        password_hash=get_password_hash(USER_PASS),
        role=UserRole.USER,
        is_active=True
    )
    db.add(user)
    
    # Create admin user
    admin = user_service.User(
        name="Admin User",
        email=ADMIN_EMAIL,
        password_hash=get_password_hash(ADMIN_PASS),
        role=UserRole.ADMIN,
        is_active=True
    )
    db.add(admin)
    db.flush()


# ---------------------------------------------------------------------------
# 1. Test: Successful Login returns JWT
# ---------------------------------------------------------------------------
def test_login_success(client, setup_users):
    """POST /api/v1/auth/login with valid credentials returns a token."""
    response = client.post(
        "/api/v1/auth/login",
        data={"username": USER_EMAIL, "password": USER_PASS}
    )
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"


# ---------------------------------------------------------------------------
# 2. Test: Failed Login (Wrong Password / Email)
# ---------------------------------------------------------------------------
def test_login_wrong_password(client, setup_users):
    """POST /api/v1/auth/login with wrong password returns 401."""
    response = client.post(
        "/api/v1/auth/login",
        data={"username": USER_EMAIL, "password": "WrongPassword!"}
    )
    assert response.status_code == 401
    assert response.json()["detail"] == "Incorrect email or password"


def test_login_nonexistent_user(client):
    """POST /api/v1/auth/login with non-existent email returns 401."""
    response = client.post(
        "/api/v1/auth/login",
        data={"username": "nobody@example.com", "password": "Password123"}
    )
    assert response.status_code == 401


# ---------------------------------------------------------------------------
# 3. Test: Protected Endpoint (/me)
# ---------------------------------------------------------------------------
def test_get_me_success(client, setup_users):
    """GET /api/v1/auth/me returns user data when authenticated."""
    # First login to get token
    login_resp = client.post(
        "/api/v1/auth/login",
        data={"username": USER_EMAIL, "password": USER_PASS}
    )
    token = login_resp.json()["access_token"]
    
    # Then use token
    response = client.get(
        "/api/v1/auth/me",
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["email"] == USER_EMAIL
    assert "password_hash" not in response.text


def test_get_me_unauthenticated(client):
    """GET /api/v1/auth/me without token returns 401."""
    response = client.get("/api/v1/auth/me")
    assert response.status_code == 401
    assert response.json()["detail"] == "Not authenticated"


def test_get_me_invalid_token(client):
    """GET /api/v1/auth/me with fake token returns 401."""
    response = client.get(
        "/api/v1/auth/me",
        headers={"Authorization": "Bearer fake.jwt.token"}
    )
    assert response.status_code == 401


# ---------------------------------------------------------------------------
# 4. Test: RBAC Admin Endpoint
# ---------------------------------------------------------------------------
def test_admin_endpoint_success(client, setup_users):
    """GET /api/v1/auth/admin-test succeeds for ADMIN user."""
    # Login as admin
    login_resp = client.post(
        "/api/v1/auth/login",
        data={"username": ADMIN_EMAIL, "password": ADMIN_PASS}
    )
    token = login_resp.json()["access_token"]
    
    response = client.get(
        "/api/v1/auth/admin-test",
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 200


def test_admin_endpoint_forbidden(client, setup_users):
    """GET /api/v1/auth/admin-test returns 403 for standard USER."""
    # Login as normal user
    login_resp = client.post(
        "/api/v1/auth/login",
        data={"username": USER_EMAIL, "password": USER_PASS}
    )
    token = login_resp.json()["access_token"]
    
    response = client.get(
        "/api/v1/auth/admin-test",
        headers={"Authorization": f"Bearer {token}"}
    )
    assert response.status_code == 403
    assert response.json()["detail"] == "Not enough permissions"
