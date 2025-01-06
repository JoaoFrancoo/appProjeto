package com.example.appprojeto

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Verificar token de login
        checkLoginStatus()

        auth = FirebaseAuth.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                signInWithEmailAndPassword(email, password)
            } else {
                Toast.makeText(this, "Preencha todos os campos.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signInWithEmailAndPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "signInWithEmailAndPassword: Success")
                Toast.makeText(baseContext, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()

                // Armazenar o token (timestamp do login)
                val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putLong("lastLogin", System.currentTimeMillis()) // Guardar timestamp atual
                editor.apply()

                // Redirecionar para a MainActivity
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Log.w(TAG, "signInWithEmailAndPassword: Failure", task.exception)
                Toast.makeText(baseContext, "Falha na autenticação.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkLoginStatus() {
        val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val lastLogin = sharedPreferences.getLong("lastLogin", 0) // Pega o timestamp do último login
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000 // 1 dia em milissegundos

        if (lastLogin != 0L && (currentTime - lastLogin) < oneDayInMillis) {
            // Se o último login foi há menos de 1 dia, redirecionar para MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        private const val TAG = "EmailandPassword"
    }
}
