"""
MedPriority — Vehicle API Endpoints
======================================
All endpoints require JWT authentication.
user_id is ALWAYS derived from the token — never from the request body.

Endpoints:
    POST   /api/v1/vehicles              Register new vehicle
    GET    /api/v1/vehicles              List current user's vehicles
    GET    /api/v1/vehicles/{id}         Get a specific vehicle
    PUT    /api/v1/vehicles/{id}         Update a vehicle
    DELETE /api/v1/vehicles/{id}         Delete a vehicle
"""

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.database.connection import get_db
from app.models.user import User
from app.schemas.vehicle import (
    VehicleCreate,
    VehicleUpdate,
    VehicleResponse,
    VehicleCreatedResponse,
    VehicleListResponse,
)
from app.services import vehicle_service
from app.api import deps

router = APIRouter(
    prefix="/vehicles",
    tags=["Vehicles"],
)


@router.post(
    "/",
    response_model=VehicleCreatedResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Register a new vehicle",
)
def register_vehicle(
    vehicle_data: VehicleCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Registers a new vehicle for the authenticated user.
    - user_id is taken from the JWT — cannot be spoofed via request body.
    - Returns 409 if vehicle_number is already registered.
    """
    vehicle = vehicle_service.create_vehicle(db, vehicle_data, current_user)
    return VehicleCreatedResponse(data=VehicleResponse.model_validate(vehicle))


@router.get(
    "/",
    response_model=VehicleListResponse,
    summary="List your vehicles",
)
def list_vehicles(
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Returns vehicles belonging to the authenticated user.
    Admins see all vehicles in the system.
    """
    vehicles = vehicle_service.get_user_vehicles(db, current_user)
    return VehicleListResponse(
        count=len(vehicles),
        data=[VehicleResponse.model_validate(v) for v in vehicles],
    )


@router.get(
    "/{vehicle_id}",
    response_model=VehicleResponse,
    summary="Get a specific vehicle",
)
def get_vehicle(
    vehicle_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Returns a vehicle by ID.
    - 404 if vehicle doesn't exist.
    - 403 if the vehicle belongs to a different user.
    """
    return vehicle_service.get_vehicle_by_id(db, vehicle_id, current_user)


@router.put(
    "/{vehicle_id}",
    response_model=VehicleResponse,
    summary="Update a vehicle",
)
def update_vehicle(
    vehicle_id: int,
    update_data: VehicleUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Partially updates a vehicle (owner_name, type, color).
    - vehicle_number cannot be changed after registration.
    - User can only update their own vehicle.
    """
    return vehicle_service.update_vehicle(db, vehicle_id, update_data, current_user)


@router.delete(
    "/{vehicle_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Delete a vehicle",
)
def delete_vehicle(
    vehicle_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_active_user),
):
    """
    Deletes a vehicle.
    - User can only delete their own vehicle.
    - Returns 204 No Content on success.
    """
    vehicle_service.delete_vehicle(db, vehicle_id, current_user)
