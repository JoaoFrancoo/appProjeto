package com.example.appprojeto

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appprojeto.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        val profileImage: ImageView = findViewById(R.id.profileImage)
        val changeProfileImage: Button = findViewById(R.id.changeProfileImage)
        val showPassword: Button = findViewById(R.id.showPassword)
        val goToChangePassword: Button = findViewById(R.id.goToChangePassword)
        val userName: TextView = findViewById(R.id.userName)
        val userEmail: TextView = findViewById(R.id.userEmail)
        val userRoutes: TextView = findViewById(R.id.userRoutes)
        val userEcopoints: TextView = findViewById(R.id.userEcopoints)

        // Recuperar UID do usuário logado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish() // Fecha a activity caso não haja usuário logado
            return
        }

        val userId = currentUser.uid

        // Buscar dados do usuário na Firestore
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: "Unknown"
                    val email = document.getString("email") ?: "Unknown"
                    val routesCount = document.getLong("routesCount")?.toInt() ?: 0
                    val ecopointsCount = document.getLong("ecopointsCount")?.toInt() ?: 0

                    // Configurando os dados do usuário
                    userName.text = name
                    userEmail.text = email
                    userRoutes.text = "Routes: $routesCount"
                    userEcopoints.text = "Ecopoints: $ecopointsCount"
                } else {
                    Toast.makeText(this, "No user data found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        // Botão para visualizar senha (exibição simulada)
        showPassword.setOnClickListener {
            Toast.makeText(this, "Your password is: 123456 (mocked)", Toast.LENGTH_SHORT).show()
        }

        // Botão para trocar imagem de perfil (simula funcionalidade)
        changeProfileImage.setOnClickListener {
            Toast.makeText(this, "Change profile image feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Botão para navegar até a página de alteração de senha
        goToChangePassword.setOnClickListener {
            val intent = Intent(this, ChangePasswordActivity::class.java)
            startActivity(intent)
        }
    }
}
