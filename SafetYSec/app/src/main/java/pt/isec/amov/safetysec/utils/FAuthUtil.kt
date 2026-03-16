package pt.isec.amov.safetysec.utils

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class FAuthUtil {
    companion object {
        private val auth by lazy { Firebase.auth }

        val currentUser: FirebaseUser?
            get() = auth.currentUser

        fun createUserWithEmail(
            email: String,
            password: String,
            onResult: (Throwable?) -> Unit
        ) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    onResult(null)
                }
                .addOnFailureListener { e ->
                    onResult(e)
                }
        }

        fun signInWithEmail(
            email: String,
            password: String,
            onResult: (Throwable?) -> Unit
        ) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    onResult(null)
                }
                .addOnFailureListener { e ->
                    onResult(e)
                }
        }

        fun signOut() {
            auth.signOut()
        }
    }
}
