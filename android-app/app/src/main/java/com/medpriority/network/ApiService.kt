package com.medpriority.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @POST("api/v1/emergencies/{id}/location")
    suspend fun sendLocation(
        @Path("id") emergencyId: Int,
        @Body location: LocationUpdateRequest
    ): Response<LocationUpdateResponse>
}
