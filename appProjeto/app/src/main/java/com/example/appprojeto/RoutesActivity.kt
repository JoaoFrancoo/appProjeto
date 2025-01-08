package com.example.appprojeto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint

class RoutesActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var routesListView: ListView
    private lateinit var routes: MutableList<RouteData>
    private lateinit var routesIds: MutableList<String> // Armazenar os IDs das rotas no Firestore

    data class RouteData(
        val points: List<GeoPoint>,
        val distance: Double,
        val duration: Double,
        val instructions: List<String>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routes)

        firestore = FirebaseFirestore.getInstance()
        routesListView = findViewById(R.id.routes_list)
        routes = mutableListOf() // Iniciar a lista mutável
        routesIds = mutableListOf() // Iniciar a lista de IDs

        val uid = intent.getStringExtra("uid")
        Log.d("RoutesActivity", "Received UID: $uid")

        if (uid != null) {
            loadRoutes(uid)
        } else {
            Log.e("RoutesActivity", "UID is null")
        }
    }

    private fun loadRoutes(uid: String) {
        Log.d("RoutesActivity", "Loading routes for UID: $uid")
        firestore.collection("routes")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { result ->
                Log.d("RoutesActivity", "Successfully fetched routes")
                routes.clear() // Limpa as rotas antes de adicionar as novas
                routesIds.clear() // Limpa os IDs das rotas

                result.forEach { document ->
                    Log.d("RoutesActivity", "Document: ${document.data}")
                    val points = document.data["points"] as? List<Map<String, Double>>
                    val distance = document.data["distance"] as? Double ?: 0.0
                    val duration = document.data["duration"] as? Double ?: 0.0
                    val instructions = (document.data["instructions"] as? List<Map<String, String>>)?.mapNotNull { instructionMap ->
                        instructionMap["instruction"] ?: ""
                    } ?: emptyList()

                    val routePoints = points?.mapNotNull {
                        val lat = it["latitude"]
                        val lon = it["longitude"]
                        if (lat != null && lon != null) GeoPoint(lat, lon) else null
                    } ?: emptyList()

                    val routeId = document.id // Armazena o ID do documento para deletar depois
                    routes.add(RouteData(routePoints, distance, duration, instructions))
                    routesIds.add(routeId) // Armazena o ID da rota
                }

                // Adapter personalizado para exibir a lista com ícone de lixeira
                val adapter = object : ArrayAdapter<RouteData>(this, R.layout.route_item, routes) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.route_item, parent, false)

                        // Obter a rota e ID correspondentes
                        val route = getItem(position)
                        val routeId = routesIds[position]

                        // Configurar o texto da rota
                        val routeInfoTextView = view.findViewById<TextView>(R.id.route_info)
                        routeInfoTextView.text = "Distância: %.2f km, Duração: %.1f horas".format(route?.distance, route?.duration)

                        // Configurar o ícone de lixeira
                        val deleteIcon = view.findViewById<ImageView>(R.id.delete_icon)
                        deleteIcon.setOnClickListener {
                            showDeleteConfirmationDialog(routeId) // Chama a função de confirmação
                        }

                        return view
                    }
                }

                routesListView.adapter = adapter

                // Clique normal para visualizar a rota
                routesListView.setOnItemClickListener { _, _, position, _ ->
                    val selectedRoute = routes[position]

                    val pointsList = ArrayList<HashMap<String, Double>>().apply {
                        selectedRoute.points.forEach { geoPoint ->
                            add(hashMapOf("latitude" to geoPoint.latitude, "longitude" to geoPoint.longitude))
                        }
                    }

                    val resultIntent = Intent().apply {
                        putExtra("type", "route")
                        putExtra("points", pointsList)
                        putExtra("distance", selectedRoute.distance)
                        putExtra("duration", selectedRoute.duration)
                        putStringArrayListExtra("instructions", ArrayList(selectedRoute.instructions))
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("RoutesActivity", "Error loading routes", e)
            }
    }

    // Função para mostrar o diálogo de confirmação para excluir a rota
    private fun showDeleteConfirmationDialog(routeId: String) {
        AlertDialog.Builder(this)
            .setTitle("Excluir Rota")
            .setMessage("Você tem certeza que deseja excluir esta rota?")
            .setPositiveButton("Sim") { _, _ ->
                deleteRoute(routeId) // Exclui a rota do Firestore
            }
            .setNegativeButton("Não", null)
            .show()
    }

    // Função para excluir a rota do Firestore
    private fun deleteRoute(routeId: String) {
        firestore.collection("routes").document(routeId)
            .delete()
            .addOnSuccessListener {
                Log.d("RoutesActivity", "Route successfully deleted")
                Toast.makeText(this, "Rota excluída com sucesso", Toast.LENGTH_SHORT).show()

                // Recarregar as rotas após a exclusão
                loadRoutes(intent.getStringExtra("uid") ?: "")
            }
            .addOnFailureListener { e ->
                Log.e("RoutesActivity", "Error deleting route", e)
                Toast.makeText(this, "Falha ao excluir a rota", Toast.LENGTH_SHORT).show()
            }
    }
}

