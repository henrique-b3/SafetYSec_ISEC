package pt.isec.amov.safetysec.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.model.RuleType
import pt.isec.amov.safetysec.model.SafetyAlert
import pt.isec.amov.safetysec.model.SafetyRule
import pt.isec.amov.safetysec.repository.DashboardRepository
import pt.isec.amov.safetysec.repository.AssociationRepository
import pt.isec.amov.safetysec.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pt.isec.amov.safetysec.utils.AuthErrorHandler

class DashboardViewModel : ViewModel() {
    private val repo = DashboardRepository()
    private val authRepo = AuthRepository()

    var alertHistory by mutableStateOf<List<SafetyAlert>>(emptyList())
    var alerts by mutableStateOf<List<SafetyAlert>>(emptyList()) // Alertas recebidos pelo Monitor
        private set
    var operationErrorResId by mutableStateOf<Int?>(null)

    // Regras do utilizador protegido carregado atualmente
    var currentRules by mutableStateOf<List<SafetyRule>>(emptyList())
        private set
    fun fetchRules(userId: String) {
        viewModelScope.launch {
            val result = repo.getRules(userId)
            result.onSuccess { rules -> currentRules = rules }
        }
    }

    fun fetchAlertHistory(userId: String) {
        viewModelScope.launch {
            val result = repo.getAlertHistory(userId)
            result.onSuccess { history -> alertHistory = history }
        }
    }

    // Monitor: Começa a ouvir os SEUS alertas
    fun startListeningAlerts() {
        val currentMonitor = authRepo.getCurrentUser() ?: return
        viewModelScope.launch {
            repo.getAlertsStream(currentMonitor.uid).collect { newAlerts ->
                alerts = newAlerts
            }
        }
    }

    fun addRule(userId: String, rule: SafetyRule) {
        viewModelScope.launch {
            repo.addRule(userId, rule)
            fetchRules(userId) // Recarrega lista
        }
    }

    fun deleteRule(userId: String, ruleId: String) {
        if (ruleId.isBlank()) return
        viewModelScope.launch {
            val result = repo.deleteRule(userId, ruleId)
            if (result.isSuccess) fetchRules(userId)
        }
    }

    // --- ENVIO DE ALERTAS (Protegido) ---
    fun sendAlert(
        protectedId: String,
        protectedName: String,
        location: String,
        type: RuleType,
        videoUrl: String,
        targetMonitorIds: List<String>,
        onSuccess: (String) -> Unit
    ) {
        if (targetMonitorIds.isEmpty()) return

        viewModelScope.launch {
            var lastAlertId = ""

            targetMonitorIds.forEach { monitorId ->
                val alert = SafetyAlert(
                    monitorId = monitorId,
                    protectedId = protectedId,
                    protectedName = protectedName,
                    type = type,
                    coordinates = location,
                    status = "ACTIVE",
                    videoUrl = videoUrl
                )

                // Enviar individualmente
                val result = repo.sendAlert(alert)
                result.onSuccess { id -> lastAlertId = id }
            }

            if (lastAlertId.isNotEmpty()) {
                onSuccess(lastAlertId)
            }
        }
    }

    fun cancelAlertStatus(alertId: String) {
        FirebaseFirestore.getInstance().collection("alerts")
            .document(alertId)
            .update("status", "CANCELLED")
    }

    fun resolveAlert(alertId: String) {
        if (alertId.isBlank()) return

        operationErrorResId = null

        FirebaseFirestore.getInstance().collection("alerts")
            .document(alertId)
            .update("status", "RESOLVED")
            .addOnFailureListener { e ->
                operationErrorResId = AuthErrorHandler.getErrorResourceId(e)
            }
    }
}