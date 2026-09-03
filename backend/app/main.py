"""
MedPriority — FastAPI Application Entry Point
==============================================
This is the main file that creates and configures the FastAPI application.
Uvicorn loads this file when you run: uvicorn app.main:app --reload

Current endpoints (Day 2):
    GET /           → Confirms the API is running
    GET /health     → Health check (will include DB check from Day 3)

Future middleware added here (later weeks):
    - CORS
    - Rate limiting
    - JWT authentication
    - Audit logging
    - Error handling
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings


# -----------------------------------------------------------------------------
# Create the FastAPI application instance
# -----------------------------------------------------------------------------
app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description=(
        "MedPriority: A Secure Real-Time Emergency Visibility and Assistance "
        "System for Private Medical Transport Vehicles"
    ),
    # Disable auto-generated docs in production
    docs_url="/docs" if settings.DEBUG else None,
    redoc_url="/redoc" if settings.DEBUG else None,
)


# -----------------------------------------------------------------------------
# CORS Middleware
# Allows the Android app (and any web client) to make requests to this API.
# In production, restrict origins to your actual domain.
# -----------------------------------------------------------------------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# -----------------------------------------------------------------------------
# Routes — Day 2
# More routes will be added as separate router modules from Week 2 onwards.
# -----------------------------------------------------------------------------

@app.get("/", tags=["Root"])
async def root():
    """
    Root endpoint.
    Confirms the MedPriority API is running.
    """
    return {
        "status": "success",
        "message": "MedPriority API Running",
        "version": settings.APP_VERSION,
        "environment": settings.APP_ENV,
    }


@app.get("/health", tags=["Health"])
async def health_check():
    """
    Health check endpoint.
    Used by the Android app (Day 5) to verify backend connectivity.
    Will include database connectivity check from Day 3.

    Returns:
        status: "ok" if everything is healthy
        api: always "ok" if this endpoint responds
        database: "not_configured" until Day 3
    """
    return {
        "status": "ok",
        "api": "ok",
        "database": "not_configured",   # will change to "connected" on Day 3
        "version": settings.APP_VERSION,
    }
