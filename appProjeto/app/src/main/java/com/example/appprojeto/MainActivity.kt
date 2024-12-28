package com.example.appprojeto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.maps.android.PolyUtil
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private val orsApiKey = "5b3ce3597851110001cf62480772659017c046b2b2c640745b510ef6"
    private lateinit var orsService: OpenRouteService

    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configuração do Retrofit com Interceptor para logs
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        orsService = retrofit.create(OpenRouteService::class.java)

        // Configuração do OpenStreetMap
        Configuration.getInstance()
            .load(applicationContext, applicationContext.getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true)

        val fabCenterUser: FloatingActionButton = findViewById(R.id.fab_center_user)
        fabCenterUser.setOnClickListener { setupUserLocation() }

        setupUserLocation()

        // Captura de toques no mapa
        map.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                val tappedLocation = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                handleMapTap(tappedLocation)
                return true
            }
        })
    }

    private fun handleMapTap(location: GeoPoint) {
        if (startPoint == null) {
            startPoint = location
            addMarker(location, "Ponto de Partida")
            Toast.makeText(this, "Ponto de Partida Adicionado!", Toast.LENGTH_SHORT).show()
        } else if (endPoint == null) {
            endPoint = location
            addMarker(location, "Destino")
            Toast.makeText(this, "Destino Adicionado! Calculando Rota...", Toast.LENGTH_SHORT).show()
            calculateRoute()
        } else {
            Toast.makeText(this, "Rota já foi calculada. Reinicie para testar novamente.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMarker(location: GeoPoint, title: String) {
        val marker = Marker(map)
        marker.position = location
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun calculateRoute() {
        val start = startPoint
        val end = endPoint

        if (start == null || end == null) {
            Toast.makeText(this, "Por favor, adicione o ponto de partida e o destino.", Toast.LENGTH_SHORT).show()
            return
        }
        val startCoord = "${start.longitude},${start.latitude}"
        val endCoord = "${end.longitude},${end.latitude}"

        Log.d("MainActivity", "Coordenadas Enviadas: Start: $startCoord, End: $endCoord")

        val call = orsService.getRoute(startCoord, endCoord, orsApiKey)
        call.enqueue(object : Callback<ORSRouteResponse> {
            override fun onResponse(call: Call<ORSRouteResponse>, response: Response<ORSRouteResponse>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("MainActivity", "Resposta da API: ${body.toString()}")

                    if (body != null && !body.routes.isNullOrEmpty()) {
                        val route = body.routes.firstOrNull()
                        if (route != null) {
                            val decodedPoints = decodeGeometry(route.geometry)
                            if (decodedPoints.isNotEmpty()) {
                                drawRoute(decodedPoints)
                                val distance = route.summary.distance / 1000.0 // km
                                val time = route.summary.duration / 3600.0   // horas
                                Toast.makeText(
                                    this@MainActivity,
                                    "Rota calculada! Distância: %.2f km, Tempo estimado: %.1f horas".format(distance, time),
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Erro ao decodificar a geometria da rota.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("MainActivity", "Erro: Nenhuma rota encontrada.")
                            Toast.makeText(this@MainActivity, "Nenhuma rota encontrada.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("MainActivity", "Erro: Resposta da API vazia ou sem rotas.")
                        Toast.makeText(this@MainActivity, "Erro: Resposta da API vazia ou sem rotas.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Erro desconhecido"
                    Log.e("API_ERROR", "Código: ${response.code()}, Mensagem: ${response.message()}, Corpo: $errorBody")
                    Toast.makeText(this@MainActivity, "Erro da API: Código ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ORSRouteResponse>, t: Throwable) {
                Log.e("MainActivity", "Erro ao calcular rota: ${t.message}")
                Toast.makeText(this@MainActivity, "Erro ao calcular rota: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun decodeGeometry(geometry: String?): List<GeoPoint> {
        return try {
            if (geometry != null) {
                PolyUtil.decode(geometry).map { latLng -> GeoPoint(latLng.latitude, latLng.longitude) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao decodificar a geometria: ${e.message}")
            emptyList()
        }
    }

    private fun drawRoute(routePoints: List<GeoPoint>) {
        if (routePoints.isNotEmpty()) {
            val polyline = Polyline()
            polyline.setPoints(routePoints)
            polyline.title = "Rota Calculada"
            map.overlays.add(polyline)
            map.invalidate()
        } else {
            Log.e("MainActivity", "Nenhum ponto de rota para desenhar.")
        }
    }

    private fun setupUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val defaultLocation = GeoPoint(38.7169, -9.1399) // Lisboa
            centerMapOnLocation(defaultLocation)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun centerMapOnLocation(location: GeoPoint) {
        map.controller.setZoom(15.0)
        map.controller.animateTo(location)
        map.invalidate()
    }
}
