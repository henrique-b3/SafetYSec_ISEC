package pt.isec.amov.safetysec.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.repository.AuthRepository
import pt.isec.amov.safetysec.utils.AuthErrorHandler

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    object MfaRequired : LoginState() // Estado intermédio
    data class Error(val errorId: Int) : LoginState()
}

class AuthViewModel : ViewModel() {
    private val repo = AuthRepository()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    var isLoading by mutableStateOf(false)

    var errorResId by mutableStateOf<Int?>(null)
    var mfaErrorResId by mutableStateOf<Int?>(null)

    fun login(email: String, pass: String) {
        isLoading = true
        errorResId = null

        viewModelScope.launch {
            val result = repo.loginUser(email, pass)

            result.onSuccess { user ->
                sendMfa()
            }
            result.onFailure { e ->
                isLoading = false
                errorResId = AuthErrorHandler.getErrorResourceId(e)
                _loginState.value = LoginState.Error(errorResId!!)
            }
        }
    }

    private fun sendMfa() {
        viewModelScope.launch {
            repo.sendMfaCode()
                .onSuccess {
                    isLoading = false
                    _loginState.value = LoginState.MfaRequired
                }
                .onFailure { e ->
                    isLoading = false
                    errorResId = AuthErrorHandler.getErrorResourceId(e)
                }
        }
    }

    fun verifyMfa(code: String) {
        isLoading = true
        mfaErrorResId = null

        viewModelScope.launch {
            val result = repo.verifyMfaCode(code)
            isLoading = false

            result.onSuccess { isValid ->
                if (isValid) {
                    _loginState.value = LoginState.Success
                } else {
                    mfaErrorResId = R.string.error_code_invalid
                }
            }
            result.onFailure { e ->
                mfaErrorResId = AuthErrorHandler.getErrorResourceId(e)
            }
        }
    }

    fun register(name: String, email: String, pass: String, cancelCode: String) {
        isLoading = true
        errorResId = null

        viewModelScope.launch {
            val result = repo.registerUser(name, email, pass, cancelCode)
            isLoading = false

            result.onSuccess {
                _loginState.value = LoginState.Success
            }
            result.onFailure { e ->
                errorResId = AuthErrorHandler.getErrorResourceId(e)
            }
        }
    }

    fun logout() {
        repo.logout()
        _loginState.value = LoginState.Idle
    }

    fun getLoggedInUser(): FirebaseUser? {
        return repo.getCurrentUser()
    }
}