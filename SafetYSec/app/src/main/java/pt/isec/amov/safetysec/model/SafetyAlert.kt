package pt.isec.amov.safetysec.model

import com.google.firebase.Timestamp

data class SafetyAlert(
    val id: String = "",
    val monitorId: String = "",
    val type: RuleType = RuleType.PANIC_BUTTON,
    val protectedId: String = "",
    val protectedName: String = "",
    val coordinates: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val videoUrl: String? = null,
    val status: String = "ACTIVE"
)