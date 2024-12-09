package com.example.appprojeto

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.appprojeto.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
    import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Usar view binding
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ajustar padding para sistema de barras
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar Firebase
        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        // Configurar botão de registro
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val birthDate = binding.etBirthDate.text.toString().trim()

            if (!validateInputs(name, email, password, birthDate)) return@setOnClickListener

            binding.btnRegister.isEnabled = false
            registerUser(name, email, password, birthDate)
        }
    }

    private fun validateInputs(name: String, email: String, password: String, birthDate: String): Boolean {
        if (name.isBlank() || email.isBlank() || password.isBlank() || birthDate.isBlank()) {
            Toast.makeText(this, "Por favor, preencha todos os campos.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email inválido.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(this, "A senha deve ter pelo menos 6 caracteres.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!birthDate.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
            Toast.makeText(this, "Data de nascimento inválida. Use o formato dd/mm/aaaa.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun registerUser(name: String, email: String, password: String, birthDate: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.btnRegister.isEnabled = true
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveUserToFirestore(it.uid, name, email, birthDate)
                    }
                    Toast.makeText(this, "Registro concluído!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else {
                    handleFirebaseError(task.exception)
                }
            }
    }

    private fun handleFirebaseError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthWeakPasswordException -> "Senha muito fraca."
            is FirebaseAuthInvalidCredentialsException -> "Email inválido."
            is FirebaseAuthUserCollisionException -> "Usuário já registrado."
            else -> exception?.localizedMessage ?: "Erro desconhecido."
        }
        Toast.makeText(this, "Erro ao registrar: $errorMessage", Toast.LENGTH_SHORT).show()
        Log.w(TAG, "Erro ao registrar usuário", exception)
    }

    private fun saveUserToFirestore(uid: String, name: String, email: String, birthDate: String) {
        val user = hashMapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "birthDate" to birthDate
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

    companion object {
        private const val TAG = "RegisterActivity"
    }
}
