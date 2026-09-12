"""
MedPriority — API v1 Router
=============================
Aggregates all v1 route modules into a single router
that is mounted at /api/v1 in main.py.

Add new route modules here as they are built:
    Week 2: auth.py      → /api/v1/auth
    Week 3: vehicles.py  → /api/v1/vehicles
    Week 3: emergency.py → /api/v1/emergency
    Week 4: location.py  → /api/v1/location
    Week 5: map.py       → /api/v1/map
    Week 6: notifications.py → /api/v1/notifications
    Week 8: audit.py     → /api/v1/audit
"""

from fastapi import APIRouter
from app.api.v1 import users, auth, vehicles, emergencies

api_router = APIRouter()

# Mount each module under its prefix
api_router.include_router(auth.router)
api_router.include_router(users.router)
api_router.include_router(vehicles.router)
api_router.include_router(emergencies.router)

# Future routers added here:
# api_router.include_router(location.router)
# api_router.include_router(notifications.router)
