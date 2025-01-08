package com.example.appprojeto

import ORSRouteResponse
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
import android.Manifest
import android.app.Activity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.example.appprojeto.classes.MapClickOverlay
import com.example.ecopointapp.EcopointManager

import org.osmdroid.views.overlay.Overlay

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_CODE_LOAD_ROUTES = 1001  // Defina a constante aqui
    }
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
    private var canSelectPoints = false  // Flag para saber se pode escolher pontos

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var fabAddEcopoint: FloatingActionButton
    private lateinit var ecopointManager: EcopointManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        fabAddEcopoint = findViewById(R.id.fab_add_ecopoint)

        ecopointManager = EcopointManager(this, map, fabAddEcopoint)

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
            .load(
                applicationContext,
                applicationContext.getSharedPreferences("osm_prefs", MODE_PRIVATE)
            )
        Configuration.getInstance().userAgentValue = packageName

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true)

        // Adicionar o listener para toques no mapa
        map.overlays.add(MapClickOverlay { geoPoint ->
            if (canSelectPoints) {
                handleMapClick(geoPoint)
            }
        })

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
                    // Ação para o item Home
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.nav_routes -> {
                    // Ação para o item Routes
                    val intent = Intent(this, RoutesActivity::class.java)
                    intent.putExtra("uid", getCurrentUserUid())
                    startActivityForResult(intent, REQUEST_CODE_LOAD_ROUTES)
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.nav_ecopoints -> {
                    // Ação para o item Ecopoints
                    val intent = Intent(this, EcopointsActivity::class.java)
                    intent.putExtra("uid", getCurrentUserUid())
                    startActivityForResult(intent, REQUEST_CODE_LOAD_ROUTES)
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.nav_leaderboard -> {
                    // Ação para o item Leaderboard
                    val intent = Intent(this, LeaderboardActivity::class.java)
                    intent.putExtra("uid", getCurrentUserUid())
                    startActivityForResult(intent, REQUEST_CODE_LOAD_ROUTES)
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.meu_perfil -> {
                    // Ação para o item Meu Perfil
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.putExtra("uid", getCurrentUserUid())
                    startActivity(intent)
                    drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.nav_logout -> {
                    // Lógica de logout
                    logout()
                    drawerLayout.closeDrawer(navView)
                    true
                }
                else -> false
            }
        }

        // Configurar FloatingActionButton
        val fabCenterUser: FloatingActionButton = findViewById(R.id.fab_center_user)
        fabCenterUser.setOnClickListener { setupUserLocation() }

        // Configurar o botão add_trail
        val fabAddTrail: FloatingActionButton = findViewById(R.id.fab_add_trail)
        fabAddTrail.setOnClickListener {
            handleAddTrail()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermissions()
    }

    private fun logout() {
        // Desconectar do Firebase
        firebaseAuth.signOut()

        // Logando o logout
        Log.d("Logout", "Usuário deslogado do Firebase")

        // Forçar a destruição do token de autenticação
        val firebaseUser = firebaseAuth.currentUser
        firebaseUser?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // O token foi atualizado (e invalidado)
                val idToken = task.result?.token
                Log.d("Logout", "Token destruído e usuário deslogado.")
            } else {
                Log.e("Logout", "Falha ao destruir o token", task.exception)
            }

            // Redirecionar para a tela de login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()  // Finaliza a activity atual para que o usuário não possa voltar com o botão de voltar
        }

        // Garantir que o usuário seja redirecionado para o login mesmo sem a necessidade de token
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
        } else {
            // Centralize a localização automaticamente
            setupUserLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Centralize a localização automaticamente após a permissão ser concedida
            setupUserLocation()
        } else {
            Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentUserUid(): String? {
        return firebaseAuth.currentUser?.uid
    }

    private fun handleAddTrail() {
        if (startPoint == null) {
            Toast.makeText(
                this,
                "Por favor, selecione o ponto de partida no mapa.",
                Toast.LENGTH_SHORT
            ).show()
            canSelectPoints = true
            return
        }

        if (endPoint == null) {
            Toast.makeText(
                this,
                "Por favor, selecione o ponto de chegada no mapa.",
                Toast.LENGTH_SHORT
            ).show()
            canSelectPoints = true
            return
        }

        calculateRoute()
    }

    private fun handleMapClick(latLon: GeoPoint) {
        // Verifica se o ponto de partida já foi definido
        if (startPoint == null) {
            startPoint = latLon
            addMarker(latLon, "Ponto de Partida")
            Toast.makeText(this, "Ponto de Partida Adicionado!", Toast.LENGTH_SHORT).show()
        } else if (endPoint == null) {
            endPoint = latLon
            addMarker(latLon, "Destino")
            Toast.makeText(this, "Destino Adicionado! Calculando Rota...", Toast.LENGTH_SHORT)
                .show()
            canSelectPoints = false  // Desativa a seleção de pontos após definir ambos
        }
    }
    private fun calculateRoute() {
        val start = startPoint
        val end = endPoint

        if (start == null || end == null) {
            Toast.makeText(
                this,
                "Por favor, adicione o ponto de partida e o destino.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val startCoord = "${start.longitude},${start.latitude}"
        val endCoord = "${end.longitude},${end.latitude}"

        Log.d("MainActivity", "Coordenadas Enviadas: Start: $startCoord, End: $endCoord")

        val call = orsService.getRoute(startCoord, endCoord, orsApiKey)
        call.enqueue(object : Callback<ORSRouteResponse> {
            override fun onResponse(
                call: Call<ORSRouteResponse>,
                response: Response<ORSRouteResponse>
            ) {
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

                                // Adicionar instruções de navegação (step instructions)
                                val instructions = segment.steps.map { step ->
                                    step.instruction
                                }

                                drawRoute(routePoints)

                                // Adicionar marcadores de instrução no mapa
                                for (step in segment.steps) {
                                    val point = routePoints[step.way_points.last()]
                                    addMarker(point, step.instruction)
                                }

                                showRouteInfoDialog(distance, duration)

                                // Chama a função de salvar, passando as instruções
                                showSaveRouteConfirmationDialog(
                                    routePoints,
                                    distance,
                                    duration,
                                    instructions  // Passa as instruções aqui
                                )
                            } else {
                                Log.e("MainActivity", "Erro: Nenhum segmento encontrado.")
                                Toast.makeText(
                                    this@MainActivity,
                                    "Nenhum segmento encontrado.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Log.e("MainActivity", "Erro: Nenhuma feature ou segmento encontrado.")
                            Toast.makeText(
                                this@MainActivity,
                                "Nenhuma feature ou segmento encontrado.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Log.e(
                            "MainActivity",
                            "Erro: Resposta da API vazia ou sem rotas. ${response.raw()}"
                        )
                        Toast.makeText(
                            this@MainActivity,
                            "Erro: Resposta da API vazia ou sem rotas.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Erro desconhecido"
                    Log.e(
                        "API_ERROR",
                        "Código: ${response.code()}, Mensagem: ${response.message()}, Corpo: $errorBody"
                    )
                    Toast.makeText(
                        this@MainActivity,
                        "Erro da API: Código ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<ORSRouteResponse>, t: Throwable) {
                Log.e("MainActivity", "Erro na API: ${t.message}", t)
                Toast.makeText(
                    this@MainActivity,
                    "Erro na comunicação com a API.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }


    private fun drawRoute(routePoints: List<GeoPoint>) {
        val polyline = Polyline()
        polyline.outlinePaint.strokeWidth = 10f
        polyline.setPoints(routePoints)
        map.overlays.add(polyline)
        map.invalidate()
    }

    private fun showRouteInfoDialog(distance: Double, duration: Double) {
        val message = "Distância: %.2f km\nDuração: %.2f horas".format(distance, duration)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Informações da Rota")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showSaveRouteConfirmationDialog(
        routePoints: List<GeoPoint>,
        distance: Double,
        duration: Double,
        instructions: List<String> // Adiciona o parâmetro instructions
    ) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Salvar Rota")
            .setMessage("Você deseja salvar esta rota?")
            .setPositiveButton("Sim") { _, _ ->
                saveRouteToFirestore(
                    routePoints,
                    distance,
                    duration,
                    instructions
                )
            }
            .setNegativeButton("Não", null)
            .show()
    }



    private fun saveRouteToFirestore(
        routePoints: List<GeoPoint>,
        distance: Double,
        duration: Double,
        instructions: List<String> // Adiciona o parâmetro instructions
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.e("Firebase", "Usuário não autenticado.")
            Toast.makeText(this, "Usuário não autenticado.", Toast.LENGTH_SHORT).show()
            return
        }

        // Filtrando apenas os pontos que você quer enviar
        // Por exemplo, você pode pegar apenas o ponto de origem e os pontos que correspondem às instruções.
        val filteredRoutePoints = routePoints.take(instructions.size + 1) // Pega o ponto de origem + número de instruções

        // Log para ver os pontos filtrados
        Log.d("RouteData", "Pontos filtrados: ${filteredRoutePoints.size}, $filteredRoutePoints")

        // Usando os pontos filtrados
        val pointsList = filteredRoutePoints.map { mapOf("latitude" to it.latitude, "longitude" to it.longitude) }

        // Cria a lista de instruções
        val instructionsList = instructions.mapIndexed { index, instruction ->
            mapOf("step" to (index + 1), "instruction" to instruction)
        }

        // Verifica e loga as instruções antes de salvar
        Log.d("RouteData", "Instruções antes de salvar no Firestore: ${instructionsList.joinToString(", ")}")

        val routeData = hashMapOf(
            "uid" to uid,
            "points" to pointsList,
            "distance" to distance,
            "duration" to duration,
            "instructions" to instructionsList,  // Instruções sendo salvas
            "timestamp" to System.currentTimeMillis()
        )

        Log.d("Firebase", "Dados a serem salvos: $routeData")

        firestore.collection("routes")
            .add(routeData)
            .addOnSuccessListener { documentReference ->
                Log.d("Firebase", "Rota salva com sucesso com ID: ${documentReference.id}")
                Toast.makeText(this, "Rota salva com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Erro ao salvar a rota: ${e.message}", e)
                Toast.makeText(this, "Erro ao salvar rota.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun setupUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
            return
        }

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val userGeoPoint = GeoPoint(location.latitude, location.longitude)
                    addMarker(userGeoPoint, "Localização Atual")
                    centerMapOnMarker(userGeoPoint)
                } else {
                    // Tentar obter a localização novamente
                    fusedLocationClient.lastLocation.addOnSuccessListener { locationRetry: Location? ->
                        if (locationRetry != null) {
                            val userGeoPointRetry = GeoPoint(locationRetry.latitude, locationRetry.longitude)
                            addMarker(userGeoPointRetry, "Localização Atual (Tentativa 2)")
                            centerMapOnMarker(userGeoPointRetry)
                        } else {
                            Toast.makeText(this, "Não foi possível obter a localização. Tente novamente.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permissão de localização necessária. Ative nas configurações.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMarker(geoPoint: GeoPoint, title: String) {
        val marker = Marker(map)
        marker.position = geoPoint
        marker.title = title
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun centerMapOnMarker(geoPoint: GeoPoint) {
        map.controller.animateTo(geoPoint)
        map.controller.setZoom(18.0)
    }
    // Declare a variável global para armazenar os overlays (rotas e marcadores)
    private val routeOverlays = mutableListOf<Overlay>()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            // Verifica o tipo de dado (rota ou ecoponto)
            val type = data.getStringExtra("type") // Este campo pode ser "route" ou "ecopoint"

            if (type == "route") {
                val pointsList = data.getSerializableExtra("points") as? ArrayList<HashMap<String, Double>>
                val distance = data.getDoubleExtra("distance", 0.0)
                val duration = data.getDoubleExtra("duration", 0.0)
                val instructions = data.getStringArrayListExtra("instructions")

                if (instructions != null) {
                    Log.d("RouteData", "Instruções: ${instructions.joinToString(", ")}")
                }

                // Converte os pontos recebidos para uma lista de GeoPoint
                val geoPoints = pointsList?.mapNotNull {
                    val latitude = it["latitude"]
                    val longitude = it["longitude"]
                    if (latitude != null && longitude != null) {
                        GeoPoint(latitude, longitude)
                    } else {
                        null
                    }
                }

                if (geoPoints != null && geoPoints.isNotEmpty()) {
                    Log.d("MainActivity", "Drawing route with Points=$geoPoints, Distance=$distance, Duration=$duration")

                    // Limpar a rota anterior (se houver)
                    removePreviousRoute()

                    // Adiciona a rota no mapa
                    val routePolyline = Polyline().apply {
                        setPoints(geoPoints)
                        color = resources.getColor(R.color.black, theme) // Cor da linha
                        width = 8.0f // Largura da linha
                    }

                    map.overlayManager.add(routePolyline) // Adiciona a nova rota ao mapa
                    routeOverlays.add(routePolyline) // Armazena a nova rota nos overlays

                    // Adiciona marcador no ponto inicial
                    val startMarker = Marker(map).apply {
                        position = geoPoints.first()
                        title = "Ponto Inicial"
                    }
                    map.overlays.add(startMarker)
                    routeOverlays.add(startMarker) // Armazena o marcador

                    // Adiciona marcador no ponto final
                    val endMarker = Marker(map).apply {
                        position = geoPoints.last()
                        title = "Ponto Final"
                    }
                    map.overlays.add(endMarker)
                    routeOverlays.add(endMarker) // Armazena o marcador

                    // Adiciona marcadores para os pontos intermediários com instruções
                    // Debugging para verificar o número de pontos intermediários e instruções
                    Log.d("MainActivity", "Número de pontos: ${geoPoints.size}")
                    Log.d("MainActivity", "Número de instruções: ${instructions?.size ?: 0}")

                    geoPoints.drop(1).dropLast(1).forEachIndexed { index, geoPoint ->
                        val instruction = instructions?.getOrNull(index) ?: "Sem instrução"

                        // Log das instruções para ver o que está sendo recuperado
                        Log.d("MainActivity", "Ponto $index: $geoPoint -> Instrução: $instruction")

                        val marker = Marker(map).apply {
                            position = geoPoint
                            title = instruction
                        }
                        map.overlays.add(marker)
                        routeOverlays.add(marker) // Armazena o marcador
                    }





                    map.invalidate() // Atualiza o mapa

                    // Centralizar o mapa na média dos pontos
                    centerMapOnRoute(geoPoints)
                } else {
                    Log.e("MainActivity", "No valid GeoPoints received to draw the route.")
                }

            } else if (type == "ecopoint") {
                // Dados para o Ecoponto
                val latitude = data.getDoubleExtra("latitude", 0.0)
                val longitude = data.getDoubleExtra("longitude", 0.0)
                val name = data.getStringExtra("name") ?: "Ecoponto"

                // Verifica se os dados do Ecoponto são válidos
                if (latitude != 0.0 && longitude != 0.0) {
                    Log.d("MainActivity", "Displaying Ecopoint at ($latitude, $longitude), Name=$name")

                    // Cria o marcador para o ecoponto
                    val ecopointMarker = Marker(map).apply {
                        position = GeoPoint(latitude, longitude)
                        title = name
                    }

                    map.overlays.add(ecopointMarker) // Adiciona o marcador no mapa
                    routeOverlays.add(ecopointMarker) // Armazena o marcador no overlay
                    map.invalidate() // Atualiza o mapa
                } else {
                    Log.e("MainActivity", "Invalid Ecopoint data received.")
                }
            } else {
                Log.e("MainActivity", "Unknown type received: $type")
            }
        }
    }


    // Função para centralizar o mapa no ponto médio da rota
    private fun centerMapOnRoute(geoPoints: List<GeoPoint>) {
        val latitudes = geoPoints.map { it.latitude }
        val longitudes = geoPoints.map { it.longitude }
        val centerLatitude = latitudes.average()
        val centerLongitude = longitudes.average()

        val centerPoint = GeoPoint(centerLatitude, centerLongitude)
        map.controller.setCenter(centerPoint) // Centraliza o mapa no ponto médio
    }

    // Função para remover rotas anteriores
    private fun removePreviousRoute() {
        // Remove todos os overlays da rota anterior
        routeOverlays.forEach { overlay ->
            map.overlayManager.remove(overlay)
        }
        routeOverlays.clear() // Limpa a lista de overlays armazenados
    }

}
