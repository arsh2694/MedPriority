"""
MedPriority — Application Configuration
========================================
Loads all settings from the .env file using Pydantic Settings.

Usage anywhere in the app:
    from app.core.config import settings
    print(settings.APP_NAME)
    print(settings.DB_PASSWORD)   # loaded securely from .env
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    All application settings.
    Values are loaded from the .env file automatically.
    If a value is missing from .env, the default defined here is used.
    """

    # -------------------------------------------------------------------------
    # Application
    # -------------------------------------------------------------------------
    APP_NAME: str = "MedPriority"
    APP_VERSION: str = "0.1.0"
    APP_ENV: str = "development"
    DEBUG: bool = True

    # -------------------------------------------------------------------------
    # FastAPI Server
    # -------------------------------------------------------------------------
    API_HOST: str = "0.0.0.0"
    API_PORT: int = 8000

    # -------------------------------------------------------------------------
    # MySQL Database (used from Day 3)
    # -------------------------------------------------------------------------
    DB_HOST: str = "localhost"
    DB_PORT: int = 3306
    DB_NAME: str = "medpriority_db"
    DB_USER: str = "medpriority_user"
    DB_PASSWORD: str = ""

    # -------------------------------------------------------------------------
    # JWT Authentication (used from Week 2)
    # -------------------------------------------------------------------------
    JWT_SECRET_KEY: str = "supersecretkey_change_in_production"
    JWT_ALGORITHM: str = "HS256"
    JWT_ACCESS_TOKEN_EXPIRE_MINUTES: int = 30

    # Emergency SOS settings
    EMERGENCY_SESSION_EXPIRY_MINUTES: int = 120  # Auto-expire active SOS after 2 hours

    JWT_REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # -------------------------------------------------------------------------
    # Firebase Cloud Messaging (used from Week 6)
    # -------------------------------------------------------------------------
    FIREBASE_CREDENTIALS_PATH: str = ""

    # -------------------------------------------------------------------------
    # CORS
    # -------------------------------------------------------------------------
    CORS_ORIGINS: str = "http://localhost:3000,http://localhost:8000"

    # -------------------------------------------------------------------------
    # Rate Limiting (used from Week 6)
    # -------------------------------------------------------------------------
    RATE_LIMIT_PER_MINUTE: int = 60
    AUTH_RATE_LIMIT_PER_MINUTE: int = 5

    # -------------------------------------------------------------------------
    # Emergency Session
    # -------------------------------------------------------------------------
    EMERGENCY_SESSION_EXPIRY_MINUTES: int = 120
    NEARBY_EMERGENCY_RADIUS_KM: float = 5.0

    # -------------------------------------------------------------------------
    # Pydantic Settings Config
    # -------------------------------------------------------------------------
    model_config = SettingsConfigDict(
        env_file=".env",          # look for .env in the directory where uvicorn is run
        env_file_encoding="utf-8",
        case_sensitive=True,      # DB_HOST != db_host
        extra="ignore",           # ignore unknown env vars
    )

    @property
    def database_url(self) -> str:
        """
        Constructs the SQLAlchemy database connection URL from individual parts.
        Passwords are URL-encoded so special characters like @ do not break the URL.
        Used by Day 3 database session setup.
        """
        from urllib.parse import quote_plus
        encoded_password = quote_plus(self.DB_PASSWORD)
        return (
            f"mysql+pymysql://{self.DB_USER}:{encoded_password}"
            f"@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}"
        )

    @property
    def cors_origins_list(self) -> list[str]:
        """Returns CORS_ORIGINS as a Python list."""
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",")]


# Single shared instance used across the entire app
# Import this wherever you need settings:
#   from app.core.config import settings
settings = Settings()
