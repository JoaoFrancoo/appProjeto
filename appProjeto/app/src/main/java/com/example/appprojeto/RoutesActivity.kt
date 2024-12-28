package com.example.appprojeto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint

class RoutesActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var routesListView: ListView
    private lateinit var routes: List<RouteData>

    data class RouteData(val points: List<GeoPoint>, val distance: Double, val duration: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routes)

        firestore = FirebaseFirestore.getInstance()
        routesListView = findViewById(R.id.routes_list)

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
        firestore.collection("user")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { result ->
                Log.d("RoutesActivity", "Successfully fetched routes")
                routes = result.map { document ->
                    Log.d("RoutesActivity", "Document: ${document.data}")
                    val points = document.data["points"] as List<Map<String, Double>>
                    val distance = document.data["distance"] as Double
                    val duration = document.data["duration"] as Double

                    val routePoints = points.map { GeoPoint(it["latitude"]!!, it["longitude"]!!) }
                    RouteData(routePoints, distance, duration)
                }

                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, routes.map {
                    "Distância: %.2f km, Duração: %.1f horas".format(it.distance, it.duration)
                })
                routesListView.adapter = adapter

                routesListView.setOnItemClickListener { _, _, position, _ ->
                    val selectedRoute = routes[position]
                    val resultIntent = Intent().apply {
                        putExtra("points", ArrayList(selectedRoute.points))
                        putExtra("distance", selectedRoute.distance)
                        putExtra("duration", selectedRoute.duration)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("RoutesActivity", "Error loading routes", e)
            }
    }
}
