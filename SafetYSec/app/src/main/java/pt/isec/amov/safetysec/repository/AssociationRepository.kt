package pt.isec.amov.safetysec.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AssociationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 2. ASSOCIAR MONITOR (Usada pelo Monitor)
    // Recebe o código, descobre quem é o protegido e cria a ligação
    suspend fun associateMonitor(monitorId: String, code: String): Result<String> {
        return try {
            val docRef = db.collection("temp_codes").document(code.uppercase())
            val snapshot = docRef.get().await()

            if (!snapshot.exists()) {
                return Result.failure(Exception("ERROR_CODE_INVALID"))
            }

            val timestamp = snapshot.getLong("timestamp") ?: 0L
            val currentTime = System.currentTimeMillis()
            val fiveMinutesInMillis = 5 * 60 * 1000

            if (currentTime - timestamp > fiveMinutesInMillis) {
                docRef.delete().await()
                return Result.failure(Exception("ERROR_CODE_EXPIRED"))
            }

            val protectedId = snapshot.getString("userId")
                ?: return Result.failure(Exception("ERROR_CODE_DATA"))

            // Bloqueio de auto-monitorização
            if (monitorId == protectedId) {
                return Result.failure(Exception("ERROR_SELF_MONITOR"))
            }

            // Executar em Batch (Tudo ou nada)
            val batch = db.batch()

            // A. Adicionar ID do Protegido à lista 'monitoring' do Monitor
            val monitorRef = db.collection("users").document(monitorId)
            batch.update(monitorRef, "monitoring", FieldValue.arrayUnion(protectedId))

            // B. Adicionar ID do Monitor à lista 'monitoredBy' do Protegido
            val protectedRef = db.collection("users").document(protectedId)
            batch.update(protectedRef, "monitoredBy", FieldValue.arrayUnion(monitorId))

            // C. Apagar o código usado (para não ser reutilizado)
            batch.delete(docRef)

            batch.commit().await()

            Result.success("SUCCESS_ASSOCIATION")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. REMOVER ASSOCIAÇÃO (Monitor remove Protegido)
    suspend fun removeAssociation(userA: String, userB: String): Result<String> {
        return try {
            val batch = db.batch()

            val docA = db.collection("users").document(userA)
            val docB = db.collection("users").document(userB)

            // Tenta remover B de A (em ambos os campos possíveis, para garantir limpeza)
            batch.update(docA, "monitoring", FieldValue.arrayRemove(userB))
            batch.update(docA, "monitoredBy", FieldValue.arrayRemove(userB))

            // Tenta remover A de B
            batch.update(docB, "monitoring", FieldValue.arrayRemove(userA))
            batch.update(docB, "monitoredBy", FieldValue.arrayRemove(userA))

            val rulesA = docA.collection("rules")
                .whereEqualTo("monitorId", userB)
                .get()
                .await()

            for (ruleDoc in rulesA.documents) {
                batch.delete(ruleDoc.reference)
            }

            // Buscar regras de B que tenham A como monitor e apagá-las (caso bidirecional)
            val rulesB = docB.collection("rules")
                .whereEqualTo("monitorId", userA)
                .get()
                .await()

            for (ruleDoc in rulesB.documents) {
                batch.delete(ruleDoc.reference)
            }

            batch.commit().await()

            Result.success("SUCCESS_ASSOCIATION_REMOVED")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateAssociationCode(userId: String): Result<String> {
        return try {
            val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val code = (1..6)
                .map { allowedChars.random() }
                .joinToString("")

            val now = System.currentTimeMillis()
            val data = hashMapOf(
                "userId" to userId,
                "timestamp" to now,
                // Campo opcional se configurar o TTL no Firebase Console (Date object para compatibilidade)
                "expireAt" to java.util.Date(now + 5 * 60 * 1000)
            )

            db.collection("temp_codes").document(code).set(data).await()

            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}