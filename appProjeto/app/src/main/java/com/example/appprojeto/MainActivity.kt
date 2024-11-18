package com.example.appprojeto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isAddingEcopoint = false
    private var isAddingTrail = false
    private val trailPoints = mutableListOf<GeoPoint>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configurações do OSM
        Configuration.getInstance()
            .load(applicationContext, applicationContext.getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true)

        setupUserLocation()

        val fabCenterUser: FloatingActionButton = findViewById(R.id.fab_center_user)
        fabCenterUser.setOnClickListener { setupUserLocation() }

        val btnAddEcopoint: Button = findViewById(R.id.btn_add_ecopoint)
        val btnAddTrail: Button = findViewById(R.id.btn_add_trail)

        // Botão para adicionar ecopontos
        btnAddEcopoint.setOnClickListener {
            isAddingEcopoint = !isAddingEcopoint
            isAddingTrail = false
            Toast.makeText(this, if (isAddingEcopoint) "Modo: Adicionar Ecoponto" else "Modo Normal", Toast.LENGTH_SHORT).show()
        }

        // Botão para adicionar trilhas
        btnAddTrail.setOnClickListener {
            isAddingTrail = !isAddingTrail
            isAddingEcopoint = false
            if (isAddingTrail) {
                trailPoints.clear() // Reinicia a lista de pontos
                Toast.makeText(this, "Modo: Criar Trilha", Toast.LENGTH_SHORT).show()
            } else {
                saveTrail()
            }
        }

        // Listener para toques no mapa
        map.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                val tappedLocation = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint

                if (isAddingEcopoint) {
                    addEcopoint(tappedLocation)
                } else if (isAddingTrail) {
                    addTrailPoint(tappedLocation)
                }
                return true
            }
        })
    }

    private fun setupUserLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLocation = GeoPoint(location.latitude, location.longitude)
                    centerMapOnLocation(userLocation)
                } else {
                    val defaultLocation = GeoPoint(38.7169, -9.1399) // Lisboa como fallback
                    centerMapOnLocation(defaultLocation)
                    Toast.makeText(this, "Não foi possível obter a localização.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun centerMapOnLocation(location: GeoPoint) {
        map.controller.setZoom(15.0)
        map.controller.animateTo(location)
        map.invalidate()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupUserLocation()
        } else {
            Toast.makeText(this, "Permissão negada.", Toast.LENGTH_LONG).show()
        }
    }

    private fun addEcopoint(location: GeoPoint) {
        val marker = Marker(map)
        marker.position = location
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Ecoponto"
        map.overlays.add(marker)
        map.invalidate()
        Toast.makeText(this, "Ecoponto adicionado!", Toast.LENGTH_SHORT).show()
    }

    private fun addTrailPoint(location: GeoPoint) {
        trailPoints.add(location)
        val marker = Marker(map)
        marker.position = location
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Ponto da Trilha"
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun saveTrail() {
        if (trailPoints.size < 2) {
            Toast.makeText(this, "A trilha precisa de pelo menos 2 pontos.", Toast.LENGTH_SHORT).show()
            return
        }

        val polyline = Polyline()
        polyline.setPoints(trailPoints)
        polyline.title = "Trilha"
        map.overlays.add(polyline)
        map.invalidate()
        Toast.makeText(this, "Trilha salva!", Toast.LENGTH_SHORT).show()
    }
}
