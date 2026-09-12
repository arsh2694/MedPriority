"""
MedPriority — Vehicle Service
================================
Business logic for vehicle operations.

Architecture rules:
  - Routers call services. No DB queries in routers.
  - user_id is ALWAYS taken from the authenticated JWT, never from request body.
  - A user can only modify/delete their own vehicles.
  - Admins can access any vehicle.
"""

from sqlalchemy.orm import Session
from fastapi import HTTPException, status

from app.models.vehicle import Vehicle
from app.models.user import User, UserRole
from app.schemas.vehicle import VehicleCreate, VehicleUpdate


# ---------------------------------------------------------------------------
# Create
# ---------------------------------------------------------------------------

def create_vehicle(db: Session, vehicle_data: VehicleCreate, current_user: User) -> Vehicle:
    """
    Registers a new vehicle for the currently authenticated user.

    Raises:
        409: If vehicle_number is already registered.
    """
    existing = db.query(Vehicle).filter(
        Vehicle.vehicle_number == vehicle_data.vehicle_number
    ).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "status": "error",
                "error_code": "VEHICLE_ALREADY_EXISTS",
                "message": f"Vehicle number '{vehicle_data.vehicle_number}' is already registered.",
            },
        )

    new_vehicle = Vehicle(
        user_id=current_user.id,           # ALWAYS from JWT, never from body
        vehicle_number=vehicle_data.vehicle_number,
        owner_name=vehicle_data.owner_name,
        vehicle_type=vehicle_data.vehicle_type,
        vehicle_color=vehicle_data.vehicle_color,
        is_verified=False,                 # verification is admin-only action
    )

    db.add(new_vehicle)
    db.commit()
    db.refresh(new_vehicle)

    return new_vehicle


# ---------------------------------------------------------------------------
# Read
# ---------------------------------------------------------------------------

def get_user_vehicles(db: Session, current_user: User) -> list[Vehicle]:
    """
    Returns all vehicles belonging to the authenticated user.
    Admins get ALL vehicles in the system.
    """
    if current_user.role == UserRole.ADMIN:
        return db.query(Vehicle).all()
    return db.query(Vehicle).filter(Vehicle.user_id == current_user.id).all()


def get_vehicle_by_id(
    db: Session,
    vehicle_id: int,
    current_user: User,
) -> Vehicle:
    """
    Fetches a vehicle by ID.

    Raises:
        404: Vehicle not found.
        403: Vehicle belongs to a different user (and current user is not admin).
    """
    vehicle = db.query(Vehicle).filter(Vehicle.id == vehicle_id).first()
    if not vehicle:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={
                "status": "error",
                "error_code": "VEHICLE_NOT_FOUND",
                "message": f"No vehicle found with ID {vehicle_id}.",
            },
        )

    # Ownership check — admins can see any vehicle
    if current_user.role != UserRole.ADMIN and vehicle.user_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={
                "status": "error",
                "error_code": "FORBIDDEN",
                "message": "You do not have permission to access this vehicle.",
            },
        )

    return vehicle


# ---------------------------------------------------------------------------
# Update
# ---------------------------------------------------------------------------

def update_vehicle(
    db: Session,
    vehicle_id: int,
    update_data: VehicleUpdate,
    current_user: User,
) -> Vehicle:
    """
    Updates a vehicle. User can only update their own vehicle.

    Raises:
        404: Vehicle not found.
        403: Not the owner.
    """
    vehicle = get_vehicle_by_id(db, vehicle_id, current_user)

    # Apply only provided fields (partial update)
    if update_data.owner_name is not None:
        vehicle.owner_name = update_data.owner_name
    if update_data.vehicle_type is not None:
        vehicle.vehicle_type = update_data.vehicle_type
    if update_data.vehicle_color is not None:
        vehicle.vehicle_color = update_data.vehicle_color

    db.commit()
    db.refresh(vehicle)

    return vehicle


# ---------------------------------------------------------------------------
# Delete
# ---------------------------------------------------------------------------

def delete_vehicle(
    db: Session,
    vehicle_id: int,
    current_user: User,
) -> None:
    """
    Deletes a vehicle. User can only delete their own vehicle.

    Raises:
        404: Vehicle not found.
        403: Not the owner.
    """
    vehicle = get_vehicle_by_id(db, vehicle_id, current_user)

    db.delete(vehicle)
    db.commit()
