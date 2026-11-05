"""
MedPriority — Admin API Endpoints
=================================
Dedicated endpoints for Admin / Dispatcher Operations Center.
All endpoints require ADMIN role.
Provides centralized management for users, fleet vehicles, emergencies, and system stats.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from pydantic import BaseModel, EmailStr, Field
from typing import Optional, List
from datetime import datetime, UTC

from app.database.connection import get_db
from app.models.user import User, UserRole
from app.models.vehicle import Vehicle, VehicleType
from app.models.emergency_session import EmergencySession, EmergencyStatus
from app.models.location import LocationUpdate
from app.core.security import get_password_hash
from app.services import emergency_service
from app.api import deps

router = APIRouter(
    prefix="/admin",
    tags=["Admin & Dispatcher"],
)


# -----------------------------------------------------------------------------
# Pydantic Schemas for Admin Operations
# -----------------------------------------------------------------------------

class AdminCreateUserRequest(BaseModel):
    name: str = Field(..., min_length=2, max_length=120)
    email: EmailStr
    password: str = Field(..., min_length=8, max_length=72)
    role: UserRole = UserRole.USER


class AdminUpdateUserStatusRequest(BaseModel):
    is_active: Optional[bool] = None
    role: Optional[UserRole] = None


class AdminCreateVehicleRequest(BaseModel):
    user_id: int
    vehicle_number: str = Field(..., min_length=3, max_length=20)
    owner_name: str = Field(..., min_length=2, max_length=120)
    vehicle_type: VehicleType = VehicleType.CAR
    vehicle_color: str = Field(..., min_length=2, max_length=50)
    is_verified: bool = True


# -----------------------------------------------------------------------------
# 1. System Statistics
# -----------------------------------------------------------------------------

@router.get("/stats", summary="Get system-wide metrics (Admin)")
def get_admin_stats(
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Returns high-level dispatcher metrics.
    """
    active_emergencies = db.query(EmergencySession).filter(
        EmergencySession.status == EmergencyStatus.ACTIVE
    ).count()

    total_emergencies = db.query(EmergencySession).count()
    total_vehicles = db.query(Vehicle).count()
    unverified_vehicles = db.query(Vehicle).filter(Vehicle.is_verified == False).count()
    total_users = db.query(User).count()

    return {
        "status": "success",
        "data": {
            "active_emergencies": active_emergencies,
            "total_emergencies": total_emergencies,
            "total_vehicles": total_vehicles,
            "unverified_vehicles": unverified_vehicles,
            "total_users": total_users,
        }
    }


# -----------------------------------------------------------------------------
# 2. User Management
# -----------------------------------------------------------------------------

@router.get("/users", summary="List all registered users (Admin)")
def list_admin_users(
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Returns all users with profile data and vehicle counts.
    """
    users = db.query(User).order_by(User.id.desc()).all()
    results = []
    for u in users:
        veh_count = db.query(Vehicle).filter(Vehicle.user_id == u.id).count()
        results.append({
            "id": u.id,
            "name": u.name,
            "email": u.email,
            "role": u.role.value,
            "is_active": u.is_active,
            "vehicles_count": veh_count,
            "created_at": u.created_at,
        })
    return {"status": "success", "count": len(results), "data": results}


@router.post("/users", status_code=status.HTTP_201_CREATED, summary="Create a user directly (Admin)")
def create_admin_user(
    user_in: AdminCreateUserRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Allows admin to create users or other admins directly from the console.
    """
    existing = db.query(User).filter(User.email == user_in.email).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "error_code": "EMAIL_ALREADY_EXISTS",
                "message": f"User with email '{user_in.email}' already exists.",
            },
        )

    new_user = User(
        name=user_in.name.strip(),
        email=str(user_in.email).lower().strip(),
        password_hash=get_password_hash(user_in.password),
        role=user_in.role,
        is_active=True,
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)

    return {
        "status": "success",
        "message": f"User '{new_user.email}' created successfully",
        "data": {
            "id": new_user.id,
            "name": new_user.name,
            "email": new_user.email,
            "role": new_user.role.value,
            "is_active": new_user.is_active,
            "created_at": new_user.created_at,
        },
    }


@router.delete("/users/{user_id}", summary="Delete a user (Admin)")
def delete_admin_user(
    user_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Deletes a user account and associated vehicles/sessions.
    Safety guard: Admin cannot delete their own active account.
    """
    if user_id == current_user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot delete your own administrator account.",
        )

    target_user = db.query(User).filter(User.id == user_id).first()
    if not target_user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found",
        )

    deleted_email = target_user.email
    db.delete(target_user)
    db.commit()

    return {
        "status": "success",
        "message": f"User '{deleted_email}' deleted successfully.",
    }


@router.patch("/users/{user_id}/status", summary="Toggle user active status or role (Admin)")
def update_admin_user_status(
    user_id: int,
    req: AdminUpdateUserStatusRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Toggles whether a user is active, or changes their role.
    """
    target_user = db.query(User).filter(User.id == user_id).first()
    if not target_user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found",
        )

    if req.is_active is not None:
        if user_id == current_user.id and req.is_active is False:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot deactivate your own administrator account.",
            )
        target_user.is_active = req.is_active

    if req.role is not None:
        target_user.role = req.role

    db.commit()
    db.refresh(target_user)

    return {
        "status": "success",
        "message": "User updated successfully",
        "data": {
            "id": target_user.id,
            "email": target_user.email,
            "role": target_user.role.value,
            "is_active": target_user.is_active,
        },
    }


# -----------------------------------------------------------------------------
# 3. Fleet & Vehicle Management
# -----------------------------------------------------------------------------

@router.get("/vehicles", summary="List all fleet vehicles with owner details (Admin)")
def list_admin_vehicles(
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Returns all vehicles across the entire system with owner info.
    """
    vehicles = db.query(Vehicle).order_by(Vehicle.id.desc()).all()
    results = []
    for v in vehicles:
        user = db.query(User).filter(User.id == v.user_id).first()
        results.append({
            "id": v.id,
            "user_id": v.user_id,
            "owner_name": v.owner_name,
            "owner_email": user.email if user else "Deleted User",
            "vehicle_number": v.vehicle_number,
            "vehicle_type": v.vehicle_type.value,
            "vehicle_color": v.vehicle_color,
            "is_verified": v.is_verified,
            "created_at": v.created_at,
        })
    return {"status": "success", "count": len(results), "data": results}


@router.post("/vehicles", status_code=status.HTTP_201_CREATED, summary="Add a vehicle directly (Admin)")
def create_admin_vehicle(
    req: AdminCreateVehicleRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Allows admin to register a vehicle for any valid user.
    """
    owner = db.query(User).filter(User.id == req.user_id).first()
    if not owner:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User ID {req.user_id} not found.",
        )

    clean_number = req.vehicle_number.strip().upper()
    existing = db.query(Vehicle).filter(Vehicle.vehicle_number == clean_number).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "error_code": "VEHICLE_ALREADY_EXISTS",
                "message": f"Vehicle '{clean_number}' is already registered.",
            },
        )

    vehicle = Vehicle(
        user_id=owner.id,
        vehicle_number=clean_number,
        owner_name=req.owner_name.strip(),
        vehicle_type=req.vehicle_type,
        vehicle_color=req.vehicle_color.strip(),
        is_verified=req.is_verified,
    )
    db.add(vehicle)
    db.commit()
    db.refresh(vehicle)

    return {
        "status": "success",
        "message": f"Vehicle '{vehicle.vehicle_number}' registered successfully",
        "data": {
            "id": vehicle.id,
            "user_id": vehicle.user_id,
            "vehicle_number": vehicle.vehicle_number,
            "owner_name": vehicle.owner_name,
            "owner_email": owner.email,
            "vehicle_type": vehicle.vehicle_type.value,
            "vehicle_color": vehicle.vehicle_color,
            "is_verified": vehicle.is_verified,
            "created_at": vehicle.created_at,
        },
    }


@router.delete("/vehicles/{vehicle_id}", summary="Delete a vehicle (Admin)")
def delete_admin_vehicle(
    vehicle_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Deletes any vehicle from the fleet.
    """
    v = db.query(Vehicle).filter(Vehicle.id == vehicle_id).first()
    if not v:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Vehicle not found",
        )

    num = v.vehicle_number
    db.delete(v)
    db.commit()

    return {
        "status": "success",
        "message": f"Vehicle '{num}' deleted successfully.",
    }


@router.put("/vehicles/{vehicle_id}/verify", summary="Toggle vehicle verification (Admin)")
def toggle_admin_vehicle_verification(
    vehicle_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Toggles the is_verified status of a vehicle.
    """
    v = db.query(Vehicle).filter(Vehicle.id == vehicle_id).first()
    if not v:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Vehicle not found",
        )

    v.is_verified = not v.is_verified
    db.commit()
    db.refresh(v)

    return {
        "status": "success",
        "message": f"Vehicle verification set to {v.is_verified}",
        "data": {
            "id": v.id,
            "vehicle_number": v.vehicle_number,
            "is_verified": v.is_verified,
        },
    }


# -----------------------------------------------------------------------------
# 4. Emergency History Log
# -----------------------------------------------------------------------------

@router.get("/history", summary="Complete emergency sessions history log (Admin)")
def get_admin_emergency_history(
    limit: int = 50,
    offset: int = 0,
    db: Session = Depends(get_db),
    current_user: User = Depends(deps.get_current_admin_user),
):
    """
    Returns full history of emergency sessions with resolution information.
    """
    sessions = (
        db.query(EmergencySession)
        .order_by(EmergencySession.id.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )

    results = []
    for s in sessions:
        user = db.query(User).filter(User.id == s.user_id).first()
        vehicle = db.query(Vehicle).filter(Vehicle.id == s.vehicle_id).first()
        admin_resolver = (
            db.query(User).filter(User.id == s.resolved_by_admin_id).first()
            if s.resolved_by_admin_id else None
        )
        loc_count = db.query(LocationUpdate).filter(LocationUpdate.session_id == s.id).count()

        results.append({
            "id": s.id,
            "user_id": s.user_id,
            "user_name": user.name if user else "Unknown User",
            "user_email": user.email if user else "",
            "vehicle_id": s.vehicle_id,
            "vehicle_number": vehicle.vehicle_number if vehicle else "Unknown",
            "vehicle_type": vehicle.vehicle_type.value if vehicle else "OTHER",
            "status": s.status.value,
            "started_at": s.started_at,
            "ended_at": s.ended_at,
            "expires_at": s.expires_at,
            "resolved_by_admin_name": admin_resolver.name if admin_resolver else None,
            "admin_notes": s.admin_notes,
            "location_points_count": loc_count,
        })

    return {
        "status": "success",
        "count": len(results),
        "data": results,
    }
