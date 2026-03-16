package pt.isec.amov.safetysec.model

data class MfaCode(
    val code: String = "",
    val email: String = "",
    val timestamp: Long = 0
)