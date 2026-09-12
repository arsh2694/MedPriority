"""
MedPriority — FastAPI Application Entry Point
==============================================
This is the main file that creates and configures the FastAPI application.
Uvicorn loads this file when you run: uvicorn app.main:app --reload

Endpoints registered here (Day 2 + Day 3):
    GET /           → Confirms the API is running
    GET /health     → Health check including live database status
    /api/v1/...     → All versioned API routes

Future middleware added here (later weeks):
    - Rate limiting
    - JWT authentication
    - Audit logging
    - Error handling
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.api.v1.router import api_router
from app.database.connection import check_database_connection


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
    docs_url="/docs" if settings.DEBUG else None,
    redoc_url="/redoc" if settings.DEBUG else None,
)


# -----------------------------------------------------------------------------
# CORS Middleware
# -----------------------------------------------------------------------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# -----------------------------------------------------------------------------
# Mount versioned API router at /api/v1
# -----------------------------------------------------------------------------
app.include_router(api_router, prefix="/api/v1")


# -----------------------------------------------------------------------------
# Root endpoint
# -----------------------------------------------------------------------------
@app.get("/", tags=["Root"])
async def root():
    """Confirms the MedPriority API is running."""
    return {
        "status": "success",
        "message": "MedPriority API Running",
        "version": settings.APP_VERSION,
        "environment": settings.APP_ENV,
    }


# -----------------------------------------------------------------------------
# Health check — now includes live database connectivity check
# -----------------------------------------------------------------------------
@app.get("/health", tags=["Health"])
async def health_check():
    """
    Health check endpoint.
    Used by the Android app to verify backend connectivity.
    Performs a live database connection test on every call.
    """
    db_status = "connected" if check_database_connection() else "unreachable"
    overall_status = "ok" if db_status == "connected" else "degraded"

    return {
        "status": overall_status,
        "api": "ok",
        "database": db_status,
        "version": settings.APP_VERSION,
    }
