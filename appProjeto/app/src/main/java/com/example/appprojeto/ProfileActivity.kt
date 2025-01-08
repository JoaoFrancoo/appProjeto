package com.example.appprojeto

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appprojeto.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.IOException

class ProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var profileImage: ImageView

    private val PICK_IMAGE_REQUEST = 71
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        profileImage = findViewById(R.id.profileImage)
        val changeProfileImage: Button = findViewById(R.id.changeProfileImage)
        val goToChangePassword: Button = findViewById(R.id.goToChangePassword)
        val userName: TextView = findViewById(R.id.userName)
        val userEmail: TextView = findViewById(R.id.userEmail)
        val userRoutes: TextView = findViewById(R.id.userRoutes)
        val userEcopoints: TextView = findViewById(R.id.userEcopoints)

        // Recuperar UID do utilizador autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Utilizador não autenticado", Toast.LENGTH_SHORT).show()
            finish() // Fecha a activity caso não haja utilizador autenticado
            return
        }

        val userId = currentUser.uid

        // Buscar dados do utilizador na Firestore
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: "Desconhecido"
                    val email = document.getString("email") ?: "Desconhecido"
                    val routesCount = document.getLong("routesCount")?.toInt() ?: 0
                    val ecopointsCount = document.getLong("ecopointsCount")?.toInt() ?: 0

                    // Configurando os dados do utilizador
                    userName.text = name
                    userEmail.text = email
                    userRoutes.text = "Rotas: $routesCount"
                    userEcopoints.text = "Ecopontos: $ecopointsCount"
                } else {
                    Toast.makeText(this, "Dados do utilizador não encontrados", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Falha ao buscar dados do utilizador: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        // Botão para trocar imagem de perfil
        changeProfileImage.setOnClickListener {
            openGallery() // Abre a galeria para selecionar uma imagem
        }

        // Botão para navegar até a página de alteração de senha
        goToChangePassword.setOnClickListener {
            val intent = Intent(this, ChangePasswordActivity::class.java)
            startActivity(intent)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            imageUri = data.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                profileImage.setImageBitmap(bitmap) // Exibe a imagem selecionada na ImageView
                uploadImageToFirebase() // Envia a imagem para o Firebase Storage
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadImageToFirebase() {
        if (imageUri != null) {
            val user = auth.currentUser
            val storageRef: StorageReference = storage.reference.child("profile_images/${user?.uid}.jpg")

            storageRef.putFile(imageUri!!)
                .addOnSuccessListener { taskSnapshot ->
                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        val imageUrl = uri.toString()
                        updateUserProfileImage(imageUrl) // Atualiza a URL da imagem no Firestore
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Falha ao enviar a imagem: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUserProfileImage(imageUrl: String) {
        val user = auth.currentUser
        val userId = user?.uid ?: return

        firestore.collection("users").document(userId)
            .update("profileImage", imageUrl)
            .addOnSuccessListener {
                Toast.makeText(this, "Imagem de perfil atualizada!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Falha ao atualizar imagem de perfil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
