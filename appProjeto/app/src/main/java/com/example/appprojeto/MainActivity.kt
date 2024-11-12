package com.example.appprojeto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.*
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configuração de bordas para o modo de tela cheia
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configuração da biblioteca OSM para cache e carregamento
        Configuration.getInstance().load(applicationContext, applicationContext.getSharedPreferences("osm_prefs", MODE_PRIVATE))

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)

        // Configuração do ponto inicial e zoom do mapa
        val startPoint = GeoPoint(38.7169, -9.1399) // Ponto corrigido
        map.controller.setZoom(15.0)
        map.controller.setCenter(startPoint)

        // Solicitar permissões e buscar ecopontos
        requestPermissions()
        fetchEcoPoints()
    }

    private fun requestPermissions() {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Aqui, adicionar lógica para mostrar a localização do usuário, se necessário
            }
        }

        when {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // Lógica de acesso à localização, se necessário
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun fetchEcoPoints() {
        val url = "https://overpass-api.de/api/interpreter?data=[out:json];node[amenity=recycling](36.9627,-9.5008,42.1543,-6.1891);out;"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string() // Extração segura do corpo da resposta
                if (responseData != null) {
                    val jsonObject = JSONObject(responseData)
                    val elements = jsonObject.getJSONArray("elements")

                    runOnUiThread {
                        for (i in 0 until elements.length()) {
                            val element = elements.getJSONObject(i)
                            val lat = element.getDouble("lat")
                            val lon = element.getDouble("lon")

                            // Criação de marcador para cada ecoponto no mapa
                            val marker = Marker(map)
                            marker.position = GeoPoint(lat, lon)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.title = "Ecoponto"
                            map.overlays.add(marker)
                        }
                        map.invalidate()  // Atualiza o mapa para exibir os marcadores
                    }
                }
            }
        })
    }
}
