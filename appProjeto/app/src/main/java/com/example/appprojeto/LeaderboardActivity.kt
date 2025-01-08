package com.example.appprojeto


import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.appprojeto.R
import com.google.firebase.firestore.FirebaseFirestore

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var leaderboardTextView: TextView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        leaderboardTextView = findViewById(R.id.leaderboardTextView)

        fetchLeaderboard()
    }

    private fun fetchLeaderboard() {
        db.collection("routes")
            .get()
            .addOnSuccessListener { result ->
                val userDistances = mutableMapOf<String, Double>()

                for (document in result) {
                    val uid = document.getString("uid") ?: continue
                    val distance = document.getDouble("distance") ?: 0.0
                    userDistances[uid] = userDistances.getOrDefault(uid, 0.0) + distance
                }

                // Ordenar pelo total de distância e pegar os top 3
                val topUsers = userDistances.entries
                    .sortedByDescending { it.value }
                    .take(3)

                // Buscar informações dos usuários
                db.collection("users")
                    .get()
                    .addOnSuccessListener { usersResult ->
                        val usersMap = usersResult.documents.associate {
                            it.getString("uid") to it.getString("name")
                        }

                        // Atualizar pódio
                        val firstPlace = topUsers.getOrNull(0)
                        val secondPlace = topUsers.getOrNull(1)
                        val thirdPlace = topUsers.getOrNull(2)

                        findViewById<TextView>(R.id.firstPlaceName).text = usersMap[firstPlace?.key] ?: "Unknown"
                        findViewById<TextView>(R.id.firstPlaceDistance).text = "${firstPlace?.value ?: 0.0} km"

                        findViewById<TextView>(R.id.secondPlaceName).text = usersMap[secondPlace?.key] ?: "Unknown"
                        findViewById<TextView>(R.id.secondPlaceDistance).text = "${secondPlace?.value ?: 0.0} km"

                        findViewById<TextView>(R.id.thirdPlaceName).text = usersMap[thirdPlace?.key] ?: "Unknown"
                        findViewById<TextView>(R.id.thirdPlaceDistance).text = "${thirdPlace?.value ?: 0.0} km"

                        // Atualizar lista completa (opcional)
                        val leaderboardText = StringBuilder("Leaderboard completo:\n")
                        topUsers.forEachIndexed { index, entry ->
                            val userName = usersMap[entry.key] ?: "Unknown"
                            leaderboardText.append("${index + 1}. $userName - ${entry.value} km\n")
                        }
                        leaderboardTextView.text = leaderboardText.toString()
                    }
                    .addOnFailureListener {
                        leaderboardTextView.text = "Erro ao buscar usuários."
                    }
            }
            .addOnFailureListener {
                leaderboardTextView.text = "Erro ao buscar rotas."
            }
    }

}
