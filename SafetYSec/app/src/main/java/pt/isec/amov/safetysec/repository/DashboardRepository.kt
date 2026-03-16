package pt.isec.amov.safetysec.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pt.isec.amov.safetysec.model.SafetyAlert
import pt.isec.amov.safetysec.model.SafetyRule

class DashboardRepository {
    private val db = FirebaseFirestore.getInstance()

    // Adicionar/Atualizar Regra
    suspend fun addRule(userId: String, rule: SafetyRule): Result<String> {
        return try {
            val collection = db.collection("users").document(userId).collection("rules")
            val docRef = if (rule.id.isNotEmpty()) collection.document(rule.id) else collection.document()

            // Garante que o ID do documento fica no objeto
            val ruleToSave = rule.copy(id = docRef.id)
            docRef.set(ruleToSave).await()

            Result.success("SUCCESS_RULE_SAVED")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRule(userId: String, ruleId: String): Result<String> {
        return try {
            db.collection("users").document(userId)
                .collection("rules").document(ruleId)
                .delete()
                .await()
            Result.success("SUCCESS_RULE_DELETED")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obter TODAS as regras (a filtragem por monitorId será feita no ViewModel/UI)
    suspend fun getRules(userId: String): Result<List<SafetyRule>> {
        return try {
            val snapshot = db.collection("users").document(userId)
                .collection("rules")
                .get()
                .await()

            val rules = snapshot.documents.mapNotNull { doc ->
                val rule = doc.toObject(SafetyRule::class.java)
                rule?.id = doc.id
                rule
            }
            Result.success(rules)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- ALERTAS ---

    // Enviar Alerta
    suspend fun sendAlert(alert: SafetyAlert): Result<String> {
        return try {
            val docRef = db.collection("alerts").document()
            val alertToSave = alert.copy(id = docRef.id, status = "ACTIVE")
            docRef.set(alertToSave).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ler Alertas (Agora filtrado pelo ID do Monitor)
    fun getAlertsStream(monitorId: String): Flow<List<SafetyAlert>> = callbackFlow {
        if (monitorId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Filtra alertas onde monitorId == Eu
        val subscription = db.collection("alerts")
            .whereEqualTo("monitorId", monitorId)
            .whereEqualTo("status", "ACTIVE")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    try {
                        val myAlerts = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(SafetyAlert::class.java)?.copy(id = doc.id)
                        }
                        trySend(myAlerts)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        trySend(emptyList())
                    }
                }
            }
        awaitClose { subscription.remove() }
    }

    // Histórico
    suspend fun getAlertHistory(userId: String): Result<List<SafetyAlert>> {
        return try {
            // Nota: O histórico mostra alertas gerados PELO utilizador protegido.
            // Podemos querer ver todos, independentemente de para quem foi enviado.
            val snapshot = db.collection("alerts")
                .whereEqualTo("protectedId", userId)
                .get()
                .await()

            val history = snapshot.toObjects(SafetyAlert::class.java)
                .sortedByDescending { it.timestamp }

            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}