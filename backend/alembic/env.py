"""
MedPriority — Alembic Migration Environment
=============================================
This file configures how Alembic connects to the database and discovers models.

Key configurations:
  - DB URL is loaded from .env via app settings (never hardcoded here)
  - target_metadata is set to Base.metadata so Alembic can detect model changes
  - Autogenerate works: `alembic revision --autogenerate` will diff models vs DB
"""

import sys
import os
from logging.config import fileConfig

from sqlalchemy import engine_from_config, pool
from alembic import context

# ---------------------------------------------------------------------------
# Make sure the backend/app package is importable from alembic/env.py
# (Alembic runs from backend/ directory, so app/ is already on the path)
# ---------------------------------------------------------------------------
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Import app settings to get the database URL
from app.core.config import settings

# Import Base and ALL models so Alembic knows about them for autogenerate
from app.database.base import Base
from app.models import user              # noqa: F401
from app.models import vehicle           # noqa: F401
from app.models import emergency_session # noqa: F401
from app.models import location          # noqa: F401

# ---------------------------------------------------------------------------
# Alembic Config object — provides access to alembic.ini values
# ---------------------------------------------------------------------------
config = context.config

# Set the database URL from our .env settings# configparser gets confused by '%' in passwords (like URL-encoded %40)
# so we replace '%' with '%%' to escape it for Alembic config interpolation
db_url = settings.database_url.replace("%", "%%")
config.set_main_option("sqlalchemy.url", db_url)

# Set up Python logging from alembic.ini
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# Target metadata — Alembic compares this against the live DB to generate diffs
target_metadata = Base.metadata


# ---------------------------------------------------------------------------
# Run migrations offline (generates SQL without connecting to DB)
# Usage: alembic upgrade head --sql
# ---------------------------------------------------------------------------
def run_migrations_offline() -> None:
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


# ---------------------------------------------------------------------------
# Run migrations online (connects to DB and applies changes directly)
# Usage: alembic upgrade head
# ---------------------------------------------------------------------------
def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
