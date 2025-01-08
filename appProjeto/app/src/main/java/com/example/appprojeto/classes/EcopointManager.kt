package com.example.ecopointapp

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.api.IMapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EcopointManager(
    private val activity: Activity,  // Alterado para Activity
    private val map: MapView,
    private val fabAddEcopoint: FloatingActionButton
) {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var canAddEcopoint = false  // Controla se o usuário pode adicionar ecopontos
    private var ecopointCounter = 1  // Contador para os nomes dos ecopontos (Ecoponto 1, Ecoponto 2, etc.)

    init {
        setupMap()
        setupFabButton()
    }
    private fun setupMap() {
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

        val mapController: IMapController = map.controller
        mapController.setCenter(GeoPoint(-23.550520, -46.633308)) // Exemplo: São Paulo
        mapController.setZoom(14.0)

        map.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Detecta o clique e converte a coordenada
                val geoPoint: GeoPoint = map.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                Log.d("EcopointManager", "Clique detectado em: Latitude = ${geoPoint.latitude}, Longitude = ${geoPoint.longitude}")

                // Verifica se podemos adicionar o ecoponto
                if (canAddEcopoint) {
                    // Chama a função para adicionar o ecoponto com um AlertDialog para o nome
                    showNameInputDialog(geoPoint)
                    return@setOnTouchListener true // Consumir o evento se for para adicionar um ecoponto
                }
            }
            // Se não for para adicionar o ecoponto, retorna false para permitir a interação com o mapa
            return@setOnTouchListener false
        }
    }

    private fun setupFabButton() {
        fabAddEcopoint.setOnClickListener {
            Log.d("EcopointManager", "'canAddEcopoint' habilitado")
            canAddEcopoint = true  // Habilita a seleção de pontos para adicionar ecoponto
            Toast.makeText(activity, "Clique no mapa para adicionar um ecoponto.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNameInputDialog(geoPoint: GeoPoint) {
        // Cria o EditText para o nome do ecoponto
        val input = EditText(activity)
        input.setHint("Nome do Ecoponto")

        // Cria o AlertDialog
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Adicionar Ecoponto")
            .setMessage("Digite o nome para o novo ecoponto:")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val ecopointName = input.text.toString().trim()

                // Se o nome for vazio, usa o nome padrão
                val name = if (ecopointName.isEmpty()) {
                    "Ecoponto $ecopointCounter"
                } else {
                    ecopointName
                }

                // Adiciona o ecoponto com o nome fornecido
                handleAddEcopoint(geoPoint, name)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
    }

    private fun handleAddEcopoint(geoPoint: GeoPoint, ecopointName: String) {
        Log.d("EcopointManager", "Iniciando a função de adicionar ecoponto")

        // Exibe um Toast para garantir que a função está sendo chamada
        Toast.makeText(activity, "Adicionando Ecoponto", Toast.LENGTH_SHORT).show()

        // Salva o ecoponto diretamente no Firestore
        saveEcopointToFirestore(geoPoint, ecopointName)
        addMarker(geoPoint, ecopointName)

        // Mensagem de sucesso
        Toast.makeText(activity, "Ecoponto '$ecopointName' adicionado!", Toast.LENGTH_SHORT).show()

        // Desativa a seleção de pontos após adicionar um ecoponto
        canAddEcopoint = false
    }

    private fun saveEcopointToFirestore(geoPoint: GeoPoint, ecopointName: String) {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            Log.e("Firebase", "Usuário não autenticado.")
            Toast.makeText(activity, "Usuário não autenticado.", Toast.LENGTH_SHORT).show()
            return
        }

        val ecopointData = hashMapOf(
            "uid" to uid,
            "latitude" to geoPoint.latitude,
            "longitude" to geoPoint.longitude,
            "name" to ecopointName,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("ecopoints")
            .add(ecopointData)
            .addOnSuccessListener { documentReference ->
                Log.d("Firebase", "Ecoponto salvo com sucesso com ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Erro ao salvar ecoponto: ${e.message}", e)
                Toast.makeText(activity, "Erro ao salvar ecoponto.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addMarker(geoPoint: GeoPoint, title: String) {
        val marker = Marker(map)
        marker.position = geoPoint
        marker.title = title
        map.overlays.add(marker)
        map.invalidate()

        Log.d("EcopointManager", "Marcador adicionado no mapa: $title")
    }
}
