package com.medpriority.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // -------------------------------------------------------------------------
    // Auth & Users
    // -------------------------------------------------------------------------

    @POST("api/v1/users/")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<UserCreatedResponse>

    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(): Response<UserDto>

    // -------------------------------------------------------------------------
    // Vehicles
    // -------------------------------------------------------------------------

    @GET("api/v1/vehicles/")
    suspend fun getVehicles(): Response<VehicleListResponse>

    @POST("api/v1/vehicles/")
    suspend fun createVehicle(
        @Body request: VehicleCreateRequest
    ): Response<VehicleCreatedResponse>

    @PUT("api/v1/vehicles/{id}")
    suspend fun updateVehicle(
        @Path("id") id: Int,
        @Body request: VehicleUpdateRequest
    ): Response<VehicleDto>

    @DELETE("api/v1/vehicles/{id}")
    suspend fun deleteVehicle(
        @Path("id") id: Int
    ): Response<Unit>

    // -------------------------------------------------------------------------
    // Emergencies
    // -------------------------------------------------------------------------

    @GET("api/v1/emergencies/active")
    suspend fun getActiveEmergency(): Response<EmergencyActiveResponse>

    @GET("api/v1/emergencies/history")
    suspend fun getEmergencyHistory(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20
    ): Response<EmergencyHistoryResponse>

    @POST("api/v1/emergencies/start")
    suspend fun startEmergency(
        @Body request: EmergencyStartRequest
    ): Response<EmergencyStartedResponse>

    @POST("api/v1/emergencies/{id}/end")
    suspend fun endEmergency(
        @Path("id") id: Int
    ): Response<EmergencyEndedResponse>

    @POST("api/v1/emergencies/{id}/location")
    suspend fun sendLocation(
        @Path("id") emergencyId: Int,
        @Body location: LocationUpdateRequest
    ): Response<LocationUpdateResponse>
}
