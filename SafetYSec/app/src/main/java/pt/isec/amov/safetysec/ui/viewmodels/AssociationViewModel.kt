package pt.isec.amov.safetysec.ui.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.model.SafetyUser
import pt.isec.amov.safetysec.repository.AssociationRepository
import pt.isec.amov.safetysec.repository.AuthRepository
import pt.isec.amov.safetysec.utils.AuthErrorHandler

class AssociationViewModel : ViewModel() {
    private val repo = AssociationRepository()
    private val authRepo = AuthRepository()
    private val TAG = "SafetySec"

    // Estados
    var generatedCode by mutableStateOf<String?>(null)
    var isGenerating by mutableStateOf(false)

    // Estados de Loading e Erro genéricos
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    var errorResId by mutableStateOf<Int?>(null)
    var successResId by mutableStateOf<Int?>(null)

    // Lista de monitores (usado pelo Protegido)
    var myMonitors by mutableStateOf<List<SafetyUser>>(emptyList())

    // --- 1. ASSOCIAR MONITOR (Ação do Monitor) ---
    fun associateMonitor(code: String, onSuccess: () -> Unit) {
        val monitorId = authRepo.getCurrentUser()?.uid

        if (monitorId == null) {
            errorMessage = "Sessão inválida. Faça login novamente."
            return
        }

        isLoading = true
        errorMessage = null
        Log.d(TAG, "Tentando associar Monitor ($monitorId) com código: $code")

        viewModelScope.launch {
            val result = repo.associateMonitor(monitorId, code)
            isLoading = false

            result.onSuccess {
                Log.d(TAG, "Associação bem sucedida!")
                onSuccess()
            }
            result.onFailure { e ->
                Log.e(TAG, "Falha na associação: ${e.message}")
                errorResId = AuthErrorHandler.getErrorResourceId(e)
            }
        }
    }

    fun generateAssociationCode() {
        val user = authRepo.getCurrentUser()
        if (user == null) {
            errorResId = R.string.error_user_not_logged
            return
        }

        isGenerating = true
        errorResId = null

        viewModelScope.launch {
            val result = repo.generateAssociationCode(user.uid)
            isGenerating = false

            result.onSuccess { code ->
                generatedCode = code
            }
            result.onFailure { e ->
                errorResId = AuthErrorHandler.getErrorResourceId(e)
                generatedCode = null
            }
        }
    }

    fun fetchMyMonitors() {
        val currentUser = authRepo.getCurrentUser() ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, _ ->
                val monitoredByIds = snapshot?.get("monitoredBy") as? List<String> ?: emptyList()

                if (monitoredByIds.isNotEmpty()) {
                    db.collection("users").whereIn("id", monitoredByIds)
                        .get()
                        .addOnSuccessListener { usersSnap ->
                            myMonitors = usersSnap.toObjects(SafetyUser::class.java)
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Erro ao buscar monitores: ${it.message}")
                        }
                } else {
                    myMonitors = emptyList()
                }
            }
    }

    fun removeAssociation(targetId: String, onSuccess: () -> Unit = {}) {
        val myId = authRepo.getCurrentUser()?.uid ?: return

        viewModelScope.launch {
            val result = repo.removeAssociation(myId, targetId)

            result.onSuccess {
                successResId = R.string.success_association_removed
                fetchMyMonitors()
                onSuccess()
            }
            result.onFailure { e ->
                errorResId = AuthErrorHandler.getErrorResourceId(e)
            }
        }
    }

    // Helpers
    fun getCurrentUser(): FirebaseUser? = authRepo.getCurrentUser()
}