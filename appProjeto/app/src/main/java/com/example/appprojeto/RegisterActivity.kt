package com.example.appprojeto

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.appprojeto.databinding.ActivityRegisterBinding
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressIndicator: CircularProgressIndicator
    private var profileImageUri: Uri? = null // Declara a variável para armazenar a URI da imagem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Botão para selecionar imagem
        binding.ivProfilePhoto.setOnClickListener {
            openImagePicker()
        }
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (!validateInputs(name, email, password)) return@setOnClickListener

            binding.btnRegister.isEnabled = false
            showProgress()

            if (profileImageUri != null) {
                uploadProfileImage(name, email, password)
            } else {
                registerUser(name, email, password, null)
            }
        }

        // Usar view binding
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Firebase
        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        // Configurar indicador de progresso
        progressIndicator = CircularProgressIndicator(this)
        progressIndicator.isIndeterminate = true

        // Ajustar padding para barras do sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configurar botão de registro
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (!validateInputs(name, email, password)) return@setOnClickListener

            binding.btnRegister.isEnabled = false
            showProgress()

            registerUser(name, email, password, null)
        }

        // Configurar botão de voltar para login
        binding.btnBackToLogin.setOnClickListener {
            // Redireciona para a LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()  // Finaliza a RegisterActivity para evitar voltar a ela
        }
    }

    private fun validateInputs(name: String, email: String, password: String): Boolean {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            showToast("Por favor, preencha todos os campos.")
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Email inválido.")
            return false
        }

        if (password.length < 6) {
            showToast("A senha deve ter pelo menos 6 caracteres.")
            return false
        }

        return true
    }

    private fun registerUser(name: String, email: String, password: String, profileImageUrl: String?) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                hideProgress()
                binding.btnRegister.isEnabled = true
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveUserToFirestore(it.uid, name, email, profileImageUrl)
                    }
                    showToast("Registro concluído!")
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else {
                    handleFirebaseError(task.exception)
                }
            }
    }

    private fun saveUserToFirestore(uid: String, name: String, email: String, profileImageUrl: String?) {
        val user = hashMapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "profileImageUrl" to profileImageUrl
        )

        firestore.collection("users")
            .document(uid)
            .set(user)
            .addOnSuccessListener {
                Log.d(TAG, "Utilizador salvo no Firestore.")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Erro ao salvar usuário no Firestore", e)
            }
    }

    private fun handleFirebaseError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthWeakPasswordException -> "Senha muito fraca."
            is FirebaseAuthInvalidCredentialsException -> "Email inválido."
            is FirebaseAuthUserCollisionException -> "Usuário já registrado."
            else -> exception?.localizedMessage ?: "Erro desconhecido."
        }
        showToast("Erro ao registrar: $errorMessage")
        Log.w(TAG, "Erro ao registrar usuário", exception)
    }

    private fun showProgress() {
        progressIndicator.show()
    }

    private fun hideProgress() {
        progressIndicator.hide()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "RegisterActivity"
        private const val PICK_IMAGE_REQUEST = 1
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(Intent.createChooser(intent, "Selecione uma imagem"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val selectedImageUri = data.data
            if (selectedImageUri != null) {
                if (profileImageUri != null) {
                    binding.ivProfilePhoto.setImageURI(profileImageUri) // Exibe a imagem no ImageView
                } else {
                    showToast("Erro: A imagem não foi carregada corretamente.")
                }

            } else {
                showToast("Erro: Nenhuma imagem foi selecionada.")
            }
        }
    }
    private fun uploadProfileImage(name: String, email: String, password: String) {
        if (profileImageUri == null) {
            showToast("Por favor, selecione uma imagem de perfil.")
            return
        }

        val storageReference = FirebaseStorage.getInstance().reference
        val fileRef = storageReference.child("profile_images/${System.currentTimeMillis()}.jpg")

        profileImageUri?.let { uri ->
            fileRef.putFile(uri)
                .addOnSuccessListener {
                    fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        registerUser(name, email, password, downloadUri.toString())
                    }
                }
                .addOnFailureListener { e ->
                    hideProgress()
                    showToast("Falha ao fazer upload da imagem: ${e.message}")
                }
        }
    }
}

