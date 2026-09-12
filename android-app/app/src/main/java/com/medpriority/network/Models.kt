package com.medpriority.network

import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------------------
// Auth & Users
// ---------------------------------------------------------------------------

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class UserCreatedResponse(
    val status: String,
    val message: String,
    val data: UserDto
)

// ---------------------------------------------------------------------------
// Vehicles
// ---------------------------------------------------------------------------

data class VehicleDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("vehicle_number") val vehicleNumber: String,
    @SerializedName("owner_name") val ownerName: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    @SerializedName("vehicle_color") val vehicleColor: String,
    @SerializedName("is_verified") val isVerified: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class VehicleListResponse(
    val count: Int,
    val data: List<VehicleDto>
)

data class VehicleCreatedResponse(
    val status: String,
    val message: String,
    val data: VehicleDto
)

data class VehicleCreateRequest(
    @SerializedName("vehicle_number") val vehicleNumber: String,
    @SerializedName("owner_name") val ownerName: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    @SerializedName("vehicle_color") val vehicleColor: String
)

data class VehicleUpdateRequest(
    @SerializedName("owner_name") val ownerName: String?,
    @SerializedName("vehicle_type") val vehicleType: String?,
    @SerializedName("vehicle_color") val vehicleColor: String?
)

// ---------------------------------------------------------------------------
// Emergencies & Location
// ---------------------------------------------------------------------------

data class EmergencySessionDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("vehicle_id") val vehicleId: Int,
    val status: String,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("created_at") val createdAt: String
)

data class EmergencyStartRequest(
    @SerializedName("vehicle_id") val vehicleId: Int
)

data class EmergencyStartedResponse(
    val status: String,
    val message: String,
    val data: EmergencySessionDto
)

data class EmergencyActiveResponse(
    val status: String? = "success",
    val data: EmergencySessionDto?
)

data class EmergencyHistoryResponse(
    val status: String = "success",
    val count: Int,
    val data: List<EmergencySessionDto>
)

data class EmergencyEndedResponse(
    val data: EmergencySessionDto
)

data class LocationUpdateRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    @SerializedName("recorded_at") val recordedAt: String
)

data class LocationUpdateResponse(
    val id: Int,
    val latitude: Double,
    val longitude: Double
)
