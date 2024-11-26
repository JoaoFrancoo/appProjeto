package com.example.appprojeto

import android.os.Bundle
import android.util.Log
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

        auth = FirebaseAuth.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun createUserWithEmailAndPassword(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Sucesso na criação do usuário
                Log.d(TAG, "createUserWithEmailAndPassword: Success")
                val user = auth.currentUser
                Toast.makeText(baseContext, "Usuário criado com sucesso!", Toast.LENGTH_SHORT).show()
            } else {
                // Falha na criação do usuário
                Log.w(TAG, "createUserWithEmailAndPassword: Failure", task.exception)
                Toast.makeText(baseContext, "Falha ao criar usuário.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun signInWithEmailAndPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Sucesso na autenticação
                Log.d(TAG, "signInWithEmailAndPassword: Success")
                val user = auth.currentUser
                Toast.makeText(baseContext, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
            } else {
                // Falha na autenticação
                Log.w(TAG, "signInWithEmailAndPassword: Failure", task.exception)
                Toast.makeText(baseContext, "Falha na autenticação.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "EmailandPassword"
    }
}
