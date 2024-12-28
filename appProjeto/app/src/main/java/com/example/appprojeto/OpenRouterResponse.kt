data class ORSRouteResponse(
    val features: List<Feature>
)

data class Feature(
    val geometry: Geometry,
    val properties: Properties
)

data class Geometry(
    val type: String,
    val coordinates: List<List<Double>>
)

data class Properties(
    val segments: List<Segment>
)

data class Segment(
    val distance: Double,
    val duration: Double,
    val steps: List<Step>
)

data class Step(
    val distance: Double,
    val duration: Double,
    val type: Int,
    val instruction: String,
    val name: String,
    val way_points: List<Int>
)
