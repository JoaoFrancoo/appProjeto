package com.example.appprojeto

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appprojeto.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        auth = FirebaseAuth.getInstance()

        val currentPassword: EditText = findViewById(R.id.currentPassword)
        val newPassword: EditText = findViewById(R.id.newPassword)
        val confirmPassword: EditText = findViewById(R.id.confirmPassword)
        val changePasswordButton: Button = findViewById(R.id.changePasswordButton)

        changePasswordButton.setOnClickListener {
            val currentPassInput = currentPassword.text.toString()
            val newPassInput = newPassword.text.toString()
            val confirmPassInput = confirmPassword.text.toString()

            if (currentPassInput.isEmpty() || newPassInput.isEmpty() || confirmPassInput.isEmpty()) {
                Toast.makeText(this, "Por favor, preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Reautenticar o utilizador com a palavra-passe atual
            val user = auth.currentUser
            if (user != null) {
                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassInput)
                user.reauthenticate(credential).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Verificar se as novas palavras-passe coincidem
                        if (newPassInput != confirmPassInput) {
                            Toast.makeText(this, "As novas palavras-passe não coincidem", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }

                        // Alterar a palavra-passe
                        user.updatePassword(newPassInput).addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                Toast.makeText(this, "Palavra-passe alterada com sucesso!", Toast.LENGTH_SHORT).show()
                                finish()  // Fechar a atividade
                            } else {
                                Toast.makeText(this, "Falha ao alterar a palavra-passe", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "A palavra-passe atual está incorreta", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Utilizador não está autenticado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
