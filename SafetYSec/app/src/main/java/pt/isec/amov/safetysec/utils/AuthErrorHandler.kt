package pt.isec.amov.safetysec.utils

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import pt.isec.amov.safetysec.R

object AuthErrorHandler {
    fun getErrorResourceId(throwable: Throwable?): Int {
        return when (throwable) {
            is FirebaseAuthInvalidUserException -> R.string.error_user_not_found
            is FirebaseAuthInvalidCredentialsException -> {
                if (throwable.errorCode == "ERROR_INVALID_EMAIL") R.string.error_invalid_email
                else R.string.error_wrong_password
            }
            is FirebaseAuthUserCollisionException -> R.string.error_email_already_in_use
            is FirebaseAuthWeakPasswordException -> R.string.error_weak_password
            else -> {
                val msg = throwable?.message ?: ""
                when {
                    msg.contains("ERROR_CODE_INVALID") -> R.string.error_code_invalid
                    msg.contains("ERROR_CODE_EXPIRED") -> R.string.error_code_expired
                    msg.contains("ERROR_CODE_DATA") -> R.string.error_code_data
                    msg.contains("ERROR_SELF_MONITOR") -> R.string.error_self_monitor
                    msg.contains("ERROR_UID_NULL") -> R.string.error_uid_null
                    msg.contains("ERROR_USER_NOT_FOUND_DB") -> R.string.error_user_not_found_db
                    msg.contains("ERROR_USER_NOT_LOGGED") -> R.string.error_user_not_logged
                    msg.contains("network") -> R.string.error_network
                    else -> R.string.error_unknown
                }
            }
        }
    }
}