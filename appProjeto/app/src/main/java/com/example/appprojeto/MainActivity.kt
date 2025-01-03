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

        Log.d("MainActivity", "Initializing MainActivity")

        // Initialize Retrofit for API calls
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

        // Map setup
        Configuration.getInstance()
            .load(applicationContext, applicationContext.getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true)

        // Drawer setup
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        menuButton = findViewById(R.id.menu_button)

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(navView)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    Log.d("MainActivity", "Home selected")
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
                    Log.d("MainActivity", "Settings selected")
                    true
                }
                else -> false
            }
        }

        val fabCenterUser: FloatingActionButton = findViewById(R.id.fab_center_user)
        fabCenterUser.setOnClickListener { setupUserLocation() }

        setupUserLocation()

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
        val uid = firebaseAuth.currentUser?.uid
        Log.d("MainActivity", "Current User UID: $uid")
        return uid
    }

    private fun handleMapTap(location: GeoPoint) {
        if (startPoint == null) {
            startPoint = location
            addMarker(location, "Ponto de Partida")
            Toast.makeText(this, "Ponto de Partida Adicionado!", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Start Point Set: $location")
        } else if (endPoint == null) {
            endPoint = location
            addMarker(location, "Destino")
            Toast.makeText(this, "Destino Adicionado! Calculando Rota...", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "End Point Set: $location")
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
        Log.d("MainActivity", "Marker Added at: $location with title: $title")
    }

    private fun calculateRoute() {
        if (startPoint == null || endPoint == null) {
            Toast.makeText(this, "Por favor, adicione o ponto de partida e o destino.", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Cannot calculate route, start or end point is null")
            return
        }

        val startCoord = "${startPoint!!.longitude},${startPoint!!.latitude}"
        val endCoord = "${endPoint!!.longitude},${endPoint!!.latitude}"
        Log.d("MainActivity", "Calculating Route: Start - $startCoord, End - $endCoord")

        val call = orsService.getRoute(startCoord, endCoord, orsApiKey)
        call.enqueue(object : Callback<ORSRouteResponse> {
            override fun onResponse(call: Call<ORSRouteResponse>, response: Response<ORSRouteResponse>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("MainActivity", "API Response: $body")

                    if (body != null) {
                        val features = body.features.firstOrNull()
                        if (features != null) {
                            val routePoints = features.geometry.coordinates.map { coord ->
                                GeoPoint(coord[1], coord[0])
                            }
                            drawRoute(routePoints)
                        } else {
                            Log.e("MainActivity", "No features found in API response")
                        }
                    }
                } else {
                    Log.e("MainActivity", "API Error: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<ORSRouteResponse>, t: Throwable) {
                Log.e("MainActivity", "API Failure: ${t.message}")
            }
        })
    }

    private fun drawRoute(routePoints: List<GeoPoint>) {
        if (routePoints.isEmpty()) {
            Log.e("MainActivity", "No points to draw")
            return
        }
        val polyline = Polyline()
        polyline.setPoints(routePoints)
        map.overlays.add(polyline)
        map.invalidate()
        Log.d("MainActivity", "Route Drawn: ${routePoints.size} points")
    }

    private fun setupUserLocation() {
        Log.d("MainActivity", "Setting up user location")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val defaultLocation = GeoPoint(38.7169, -9.1399)
            centerMapOnLocation(defaultLocation)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun centerMapOnLocation(location: GeoPoint) {
        map.controller.setZoom(15.0)
        map.controller.animateTo(location)
        map.invalidate()
        Log.d("MainActivity", "Map centered on location: $location")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_LOAD_ROUTES && resultCode == RESULT_OK) {
            val points = data?.getSerializableExtra("points") as? ArrayList<GeoPoint>
            val distance = data?.getDoubleExtra("distance", 0.0)
            val duration = data?.getDoubleExtra("duration", 0.0)

            if (points != null) {
                drawRoute(points)
                Toast.makeText(this, "Rota carregada com $distance km em $duration minutos.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nenhuma rota encontrada.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
