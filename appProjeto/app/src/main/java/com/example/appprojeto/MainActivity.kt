package com.example.appprojeto

import ORSRouteResponse
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var menuButton: ImageButton
    private lateinit var orsService: OpenRouteService
    private val orsApiKey = "5b3ce3597851110001cf62480772659017c046b2b2c640745b510ef6"
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private val REQUEST_CODE_LOAD_ROUTES = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar Retrofit para chamada à API
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

        // Configurar a interface do mapa
        Configuration.getInstance()
            .load(applicationContext, applicationContext.getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true)

        // Inicializar DrawerLayout e NavigationView
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        menuButton = findViewById(R.id.menu_button)

        // Configurar o botão de menu para abrir o Navigation Drawer
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(navView)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {

                    true
                }
                R.id.nav_routes -> {

                    val intent = Intent(this, RoutesActivity::class.java)
                    intent.putExtra("uid", getCurrentUserUid())
                    startActivityForResult(intent, REQUEST_CODE_LOAD_ROUTES)
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.nav_settings -> {

                    true
                }
                else -> false
            }
        }
        // Configurar FloatingActionButton
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

    private fun getCurrentUserUid(): String? {
        return firebaseAuth.currentUser?.uid
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

                    if (body != null && body.features != null && body.features.isNotEmpty()) {
                        val feature = body.features.firstOrNull()
                        if (feature != null && feature.properties != null && feature.properties.segments != null && feature.properties.segments.isNotEmpty()) {
                            val segment = feature.properties.segments.firstOrNull()
                            if (segment != null) {
                                val distance = segment.distance / 1000.0 // km
                                val duration = segment.duration / 3600.0 // horas

                                // Decodifica as coordenadas da geometria
                                val routePoints = feature.geometry.coordinates.map { coord ->
                                    GeoPoint(coord[1], coord[0])
                                }
                                drawRoute(routePoints)

                                // Adicionar marcadores de instrução
                                for (step in segment.steps) {
                                    val point = routePoints[step.way_points.last()]
                                    addMarker(point, step.instruction)
                                }

                                showRouteInfoDialog(distance, duration)
                                showSaveRouteConfirmationDialog(routePoints, distance, duration) // Exibir confirmação
                            } else {
                                Log.e("MainActivity", "Erro: Nenhum segmento encontrado.")
                                Toast.makeText(this@MainActivity, "Nenhum segmento encontrado.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("MainActivity", "Erro: Nenhuma feature ou segmento encontrado.")
                            Toast.makeText(this@MainActivity, "Nenhuma feature ou segmento encontrado.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("MainActivity", "Erro: Resposta da API vazia ou sem rotas. ${response.raw()}")
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

    private fun saveRouteToFirestore(routePoints: List<GeoPoint>, distance: Double, duration: Double) {
        val uid = getCurrentUserUid() ?: return

        val routeData = hashMapOf(
            "uid" to uid,
            "points" to routePoints.map { mapOf("latitude" to it.latitude, "longitude" to it.longitude) },
            "distance" to distance,
            "duration" to duration
        )

        firestore.collection("routes")
            .add(routeData)
            .addOnSuccessListener { documentReference ->
                Log.d("MainActivity", "Rota salva com ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Erro ao salvar a rota", e)
            }
    }

    private fun showRouteInfoDialog(distance: Double, duration: Double) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Informações da Rota")
        builder.setMessage("Distância: %.2f km\nTempo estimado: %.1f horas".format(distance, duration))
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun showSaveRouteConfirmationDialog(routePoints: List<GeoPoint>, distance: Double, duration: Double) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Salvar Rota")
        builder.setMessage("Deseja salvar esta rota?\n\nDistância: %.2f km\nTempo estimado: %.1f horas".format(distance, duration))
        builder.setPositiveButton("Sim") { dialog, _ ->
            saveRouteToFirestore(routePoints, distance, duration)
            dialog.dismiss()
        }
        builder.setNegativeButton("Não") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
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

    private fun loadRoutesFromFirestore() {
        val uid = getCurrentUserUid() ?: return

        firestore.collection("routes")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val points = document.data["points"] as List<Map<String, Double>>
                    val distance = document.data["distance"] as Double
                    val duration = document.data["duration"] as Double

                    val routePoints = points.map { GeoPoint(it["latitude"]!!, it["longitude"]!!) }
                    drawRoute(routePoints)

                    // Exibir informações da rota ou adicionar lógica adicional
                    showRouteInfoDialog(distance, duration)
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Erro ao carregar rotas", e)
            }
    }
}