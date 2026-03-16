package pt.isec.amov.safetysec.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pt.isec.amov.safetysec.model.MfaCode
import pt.isec.amov.safetysec.model.SafetyUser
import pt.isec.amov.safetysec.utils.FAuthUtil
import kotlin.random.Random

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    // Registo
    suspend fun registerUser(name: String, email: String, pass: String, cancelCode: String): Result<String> {
        return try {
            auth.createUserWithEmailAndPassword(email, pass).await()
            val user = auth.currentUser
            val userId = user?.uid ?: throw Exception("ERROR_UID_NULL")

            // Atualizar Perfil Auth
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            user.updateProfile(profileUpdates).await()

            // Guardar na Firestore
            val newUser = SafetyUser(id = userId, email = email, name = name, cancelCode = cancelCode)
            db.collection("users").document(userId).set(newUser).await()

            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Login: Autentica + Busca Perfil
    suspend fun loginUser(email: String, pass: String): Result<SafetyUser> {
        return try {
            auth.signInWithEmailAndPassword(email, pass).await()
            val userId = auth.currentUser?.uid ?: throw Exception("ERROR_UID_NULL")

            // Buscar dados extra (PIN, etc)
            val doc = db.collection("users").document(userId).get().await()
            val safetyUser = doc.toObject(SafetyUser::class.java) ?: throw Exception("ERROR_USER_NOT_FOUND_DB")

            Result.success(safetyUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMfaCode(): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("ERROR_USER_NOT_LOGGED"))

        val code = Random.nextInt(10000, 99999).toString()

        val mfaData = MfaCode(
            code = code,
            email = user.email ?: "",
            timestamp = System.currentTimeMillis()
        )

        return try {
            // Guarda na BD
            db.collection("mfa_codes").document(user.uid).set(mfaData).await()

            Log.e("AMOV_MFA", "   CÓDIGO SEGURANÇA (${user.email}): $code   ")

            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyMfaCode(inputCode: String): Result<Boolean> {
        val user = auth.currentUser ?: return Result.failure(Exception("ERROR_USER_NOT_LOGGED"))

        return try {
            val snapshot = db.collection("mfa_codes").document(user.uid).get().await()
            val serverData = snapshot.toObject(MfaCode::class.java)

            if (serverData != null && serverData.code == inputCode) {
                db.collection("mfa_codes").document(user.uid).delete()
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 1. RECUPERAÇÃO DE PASSWORD
    fun sendPasswordResetEmail(email: String, onResult: (Result<String>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(Result.success("SUCCESS_EMAIL_SENT")) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    //Atualizar Password
    suspend fun updatePassword(newPass: String): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("ERROR_USER_NOT_LOGGED"))

            user.updatePassword(newPass).await()

            Result.success("SUCCESS_PASS_UPDATED")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ATUALIZAR PERFIL
    suspend fun updateUserProfile(userId: String, newName: String, newCode: String): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("ERROR_USER_NOT_LOGGED"))

            // Auth
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
            user.updateProfile(profileUpdates).await()

            // Firestore
            val updates = mapOf("name" to newName, "cancelCode" to newCode)
            db.collection("users").document(userId).update(updates).await()
            Result.success("SUCCESS_UPDATED")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Função Auxiliar para obter dados completos
    suspend fun getUserData(userId: String): SafetyUser? {
        return try {
            val doc = db.collection("users").document(userId).get().await()
            doc.toObject(SafetyUser::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun logout() = FAuthUtil.signOut()
    fun getCurrentUser() = FAuthUtil.currentUser
}