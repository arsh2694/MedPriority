"""
MedPriority — Database Connection & Session Management
=======================================================
Sets up:
  - SQLAlchemy engine (the MySQL connection pool)
  - SessionLocal (session factory — creates individual DB sessions)
  - get_db() dependency (used by FastAPI route handlers)

Usage in a FastAPI route:
    from app.database.connection import get_db
    from sqlalchemy.orm import Session

    @router.get("/example")
    def example(db: Session = Depends(get_db)):
        # db is a live database session, auto-closed after request
        ...
"""

from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker, Session
from typing import Generator

from app.core.config import settings


# -----------------------------------------------------------------------------
# Engine — the actual connection to MySQL
# pool_pre_ping=True: tests the connection before each use (handles dropped connections)
# -----------------------------------------------------------------------------
engine = create_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_size=10,          # max persistent connections in pool
    max_overflow=20,       # max extra connections beyond pool_size
    echo=settings.DEBUG,   # logs every SQL statement when DEBUG=true
)


# -----------------------------------------------------------------------------
# SessionLocal — factory that creates database sessions
# autocommit=False: we control transactions manually (safer)
# autoflush=False:  changes are not sent to DB until we call commit()
# -----------------------------------------------------------------------------
SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
)


# -----------------------------------------------------------------------------
# get_db — FastAPI dependency for database sessions
# Every request that needs DB access gets a fresh session.
# The session is automatically closed when the request finishes (even on error).
# -----------------------------------------------------------------------------
def get_db() -> Generator[Session, None, None]:
    """
    FastAPI dependency that provides a database session per request.
    Use with: db: Session = Depends(get_db)
    """
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# -----------------------------------------------------------------------------
# Health check helper — used by /health endpoint
# -----------------------------------------------------------------------------
def check_database_connection() -> bool:
    """
    Attempts a simple query to verify the database is reachable.
    Returns True if connected, False otherwise.
    """
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
        return True
    except Exception:
        return False
