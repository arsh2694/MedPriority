"""
MedPriority — Test Configuration (conftest.py)
================================================
Sets up shared pytest fixtures available to all test files.

Uses SQLite in-memory database for tests — no MySQL or Docker required.
Tests run fast and in complete isolation.

Fixtures:
    engine     — in-memory SQLite engine (creates all tables)
    db         — database session for each test (rolled back after)
    client     — FastAPI TestClient with DB dependency overridden
"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import StaticPool

from app.main import app
from app.database.base import Base
from app.database.connection import get_db


# ---------------------------------------------------------------------------
# Use SQLite in memory — fast, no external dependency, isolated per test run
# ---------------------------------------------------------------------------
SQLITE_URL = "sqlite:///:memory:"


@pytest.fixture(scope="session")
def engine():
    """
    Creates a SQLite in-memory engine for the entire test session.
    All tables are created once and shared across tests.
    """
    _engine = create_engine(
        SQLITE_URL,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=_engine)
    yield _engine
    Base.metadata.drop_all(bind=_engine)


@pytest.fixture(scope="function")
def db(engine) -> Session:
    """
    Provides a database session for each individual test.
    Rolls back all changes after each test — tests are fully isolated.
    """
    TestingSessionLocal = sessionmaker(bind=engine)
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.rollback()
        session.close()


@pytest.fixture(scope="function")
def client(db) -> TestClient:
    """
    FastAPI TestClient with the real get_db dependency replaced by
    the test database session. All API calls go through the test DB.
    """
    def override_get_db():
        try:
            yield db
        finally:
            pass  # rollback handled by db fixture

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
