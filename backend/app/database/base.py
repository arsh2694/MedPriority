"""
MedPriority — SQLAlchemy Declarative Base
==========================================
Every ORM model (User, Vehicle, EmergencySession, etc.) must inherit from Base.
This is what links Python classes to MySQL tables.

Usage:
    from app.database.base import Base

    class User(Base):
        __tablename__ = "users"
        ...
"""

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """
    Shared declarative base for all MedPriority ORM models.
    SQLAlchemy uses this to track all models and generate migrations.
    """
    pass
