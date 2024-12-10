package com.example.appprojeto

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.*

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var progressIndicator: CircularProgressIndicator
    private var profileImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Usar view binding
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Firebase
        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Configurar indicador de progresso
        progressIndicator = CircularProgressIndicator(this)
        progressIndicator.isIndeterminate = true

        // Ajustar padding para barras do sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Selecionar foto
        val selectPhotoLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    profileImageUri = uri
                    binding.profileImageView.setImageURI(uri)
                }
            }

        binding.btnSelectPhoto.setOnClickListener {
            selectPhotoLauncher.launch("image/*")
        }

        // Configurar botão de registro
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val birthDate = binding.etBirthDate.text.toString().trim()

            if (!validateInputs(name, email, password, birthDate)) return@setOnClickListener

            binding.btnRegister.isEnabled = false
            showProgress()

            if (profileImageUri != null) {
                uploadProfilePhoto(name, email, password, birthDate)
            } else {
                registerUser(name, email, password, birthDate, null)
            }
        }
    }

    private fun validateInputs(name: String, email: String, password: String, birthDate: String): Boolean {
        if (name.isBlank() || email.isBlank() || password.isBlank() || birthDate.isBlank()) {
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

        if (!birthDate.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
            showToast("Data de nascimento inválida. Use o formato dd/mm/aaaa.")
            return false
        }

        return true
    }

    private fun uploadProfilePhoto(name: String, email: String, password: String, birthDate: String) {
        val filename = UUID.randomUUID().toString()
        val ref = storage.reference.child("profileImages/$filename")

        profileImageUri?.let { uri ->
            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { photoUrl ->
                        registerUser(name, email, password, birthDate, photoUrl.toString())
                    }
                }
                .addOnFailureListener { e ->
                    hideProgress()
                    binding.btnRegister.isEnabled = true
                    showToast("Erro ao enviar a foto: ${e.message}")
                }
        }
    }

    private fun registerUser(name: String, email: String, password: String, birthDate: String, photoUrl: String?) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                hideProgress()
                binding.btnRegister.isEnabled = true
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveUserToFirestore(it.uid, name, email, birthDate, photoUrl)
                    }
                    showToast("Registro concluído!")
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else {
                    handleFirebaseError(task.exception)
                }
            }
    }

    private fun saveUserToFirestore(uid: String, name: String, email: String, birthDate: String, photoUrl: String?) {
        val user = hashMapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "birthDate" to birthDate,
            "photoUrl" to photoUrl
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
    }
}
