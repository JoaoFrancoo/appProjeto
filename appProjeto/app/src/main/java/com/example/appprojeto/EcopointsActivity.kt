package com.example.appprojeto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint

class EcopointsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var ecopointsListView: ListView
    private lateinit var ecopoints: MutableList<EcopointData>
    private lateinit var ecopointsIds: MutableList<String> // Armazenar os IDs dos ecopontos no Firestore

    data class EcopointData(
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ecopoints)

        firestore = FirebaseFirestore.getInstance()
        ecopointsListView = findViewById(R.id.ecopoints_list)

        ecopoints = mutableListOf()
        ecopointsIds = mutableListOf()

        val uid = intent.getStringExtra("uid")
        Log.d("EcopointsActivity", "Received UID: $uid")

        if (uid != null) {
            loadEcopoints(uid)
        } else {
            Log.e("EcopointsActivity", "UID is null")
        }
    }

    private fun loadEcopoints(uid: String) {
        Log.d("EcopointsActivity", "Loading ecopoints for UID: $uid")
        firestore.collection("ecopoints")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { result ->
                Log.d("EcopointsActivity", "Successfully fetched ecopoints")
                ecopoints.clear() // Limpa os ecopontos antes de adicionar os novos
                ecopointsIds.clear() // Limpa os IDs dos ecopontos

                result.forEach { document ->
                    Log.d("EcopointsActivity", "Document: ${document.data}")
                    val name = document.data["name"] as? String ?: "Sem nome"
                    val latitude = document.data["latitude"] as? Double ?: 0.0
                    val longitude = document.data["longitude"] as? Double ?: 0.0

                    val ecopoint = EcopointData(name, latitude, longitude)
                    ecopoints.add(ecopoint)
                    ecopointsIds.add(document.id) // Armazena o ID do ecoponto
                }

                // Adapter personalizado para exibir a lista com ícone de lixeira
                val adapter = object : ArrayAdapter<EcopointData>(this, R.layout.ecopoint_item, ecopoints) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.ecopoint_item, parent, false)

                        // Obter o ecoponto e o ID correspondente
                        val ecopoint = getItem(position)
                        val ecopointId = ecopointsIds[position]

                        // Configurar o texto do ecoponto
                        val ecopointInfoTextView = view.findViewById<TextView>(R.id.ecopoint_info)
                        ecopointInfoTextView.text = "Nome: ${ecopoint?.name}"

                        // Configurar o ícone de lixeira
                        val deleteIcon = view.findViewById<ImageView>(R.id.delete_icon)
                        deleteIcon.setOnClickListener {
                            showDeleteConfirmationDialog(ecopointId) // Chama a função de confirmação
                        }

                        return view
                    }
                }

                ecopointsListView.adapter = adapter

                // Clique normal para visualizar os detalhes do ecoponto
                ecopointsListView.setOnItemClickListener { _, _, position, _ ->
                    val selectedEcopoint = ecopoints[position]

                    val resultIntent = Intent().apply {
                        putExtra("type", "ecopoint") // Adiciona a chave "type" para identificar que é um ecoponto
                        putExtra("name", selectedEcopoint.name)
                        putExtra("latitude", selectedEcopoint.latitude)
                        putExtra("longitude", selectedEcopoint.longitude)
                    }

                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }

            }
            .addOnFailureListener { e ->
                Log.e("EcopointsActivity", "Error loading ecopoints", e)
            }
    }

    // Função para mostrar o diálogo de confirmação para excluir o ecoponto
    private fun showDeleteConfirmationDialog(ecopointId: String) {
        AlertDialog.Builder(this)
            .setTitle("Excluir Ecoponto")
            .setMessage("Você tem certeza que deseja excluir este ecoponto?")
            .setPositiveButton("Sim") { _, _ ->
                deleteEcopoint(ecopointId) // Exclui o ecoponto do Firestore
            }
            .setNegativeButton("Não", null)
            .show()
    }

    // Função para excluir o ecoponto do Firestore
    private fun deleteEcopoint(ecopointId: String) {
        firestore.collection("ecopoints").document(ecopointId)
            .delete()
            .addOnSuccessListener {
                Log.d("EcopointsActivity", "Ecopoint successfully deleted")
                Toast.makeText(this, "Ecoponto excluído com sucesso", Toast.LENGTH_SHORT).show()

                // Recarregar os ecopontos após a exclusão
                loadEcopoints(intent.getStringExtra("uid") ?: "")
            }
            .addOnFailureListener { e ->
                Log.e("EcopointsActivity", "Error deleting ecopoint", e)
                Toast.makeText(this, "Falha ao excluir o ecoponto", Toast.LENGTH_SHORT).show()
            }
    }
}
