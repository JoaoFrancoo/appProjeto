package com.example.appprojeto

data class ORSRouteResponse(
    val routes: List<Route>
)

data class Route(
    val summary: Summary,
    val geometry: String // A geometria da rota (codificada)
)

data class Summary(
    val distance: Double, // Distância total da rota (em metros)
    val duration: Double  // Tempo estimado para percorrer a rota (em segundos)
)
