package com.medpriority.network

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
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
