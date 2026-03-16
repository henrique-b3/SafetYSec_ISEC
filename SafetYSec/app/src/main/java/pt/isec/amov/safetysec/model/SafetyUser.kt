package pt.isec.amov.safetysec.model

import com.google.firebase.firestore.PropertyName

data class SafetyUser(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val cancelCode: String = "0000",


    val monitoring: List<String> = emptyList(),


    val monitoredBy: List<String> = emptyList(),
)

