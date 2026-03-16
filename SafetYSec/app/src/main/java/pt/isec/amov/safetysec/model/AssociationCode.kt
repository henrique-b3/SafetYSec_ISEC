package pt.isec.amov.safetysec.model

import com.google.firebase.Timestamp

data class AssociationCode(
    val code: String = "",
    val userId: String = "",
    val expirationDate: Timestamp = Timestamp.now()
)