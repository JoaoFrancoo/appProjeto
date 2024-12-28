package com.example.appprojeto
import ORSRouteResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenRouteService {
    @GET("v2/directions/foot-walking")
    fun getRoute(
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("api_key") apiKey: String
    ): Call<ORSRouteResponse>
}
