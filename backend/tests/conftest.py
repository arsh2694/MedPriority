"""
MedPriority — Test Configuration (conftest.py)
================================================
Sets up shared pytest fixtures for all test files.

Uses a unique SQLite in-memory database per test.
Each test gets a completely fresh DB — guaranteed isolation.

Fixtures:
    db      — fresh per-test Session with all tables created
    client  — FastAPI TestClient with the DB dependency overridden
"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session

from app.main import app
from app.database.base import Base
from app.database.connection import get_db

# Counter used to generate unique DB file paths per test
_db_counter = 0


@pytest.fixture(scope="function")
def db(tmp_path) -> Session:
    """
    Provides a fully isolated database session per test.
    Uses a unique on-disk SQLite file in a temp directory so each test
    gets a completely independent database — zero leakage.
    """
    global _db_counter
    _db_counter += 1
    db_path = tmp_path / f"test_{_db_counter}.db"
    db_url = f"sqlite:///{db_path}"

    _engine = create_engine(
        db_url,
        connect_args={"check_same_thread": False},
    )
    Base.metadata.create_all(bind=_engine)
    TestingSessionLocal = sessionmaker(bind=_engine)
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.close()
        _engine.dispose()


@pytest.fixture(scope="function")
def client(db) -> TestClient:
    """
    FastAPI TestClient that uses the isolated per-test database session.
    """
    def override_get_db():
        try:
            yield db
        finally:
            pass

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
