package pt.isec.amov.safetysec.model

import com.google.firebase.firestore.PropertyName

enum class RuleType {
    FALL,
    ACCIDENT,
    GEOFENCING,
    SPEED,
    INACTIVITY,
    PANIC_BUTTON
}

data class SafetyRule(
    var id: String = "",

    var monitorId: String = "",

    var type: RuleType = RuleType.FALL,

    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,

    var parameters: Map<String, String> = emptyMap()
)

