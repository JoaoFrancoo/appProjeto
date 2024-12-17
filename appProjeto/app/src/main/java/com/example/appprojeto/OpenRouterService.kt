package com.example.appprojeto

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interface para acessar os serviços da API do OpenRouteService.
 */
interface OpenRouteService {

    @GET("v2/directions/foot-walking") // Caminhada como modo de transporte
    fun getRoute(
        @Query("start") start: String,  // Coordenadas do ponto de partida (longitude,latitude)
        @Query("end") end: String,      // Coordenadas do destino (longitude,latitude)
        @Query("api_key") apiKey: String // Chave da API do OpenRouteService
    ): Call<ORSRouteResponse>
}
