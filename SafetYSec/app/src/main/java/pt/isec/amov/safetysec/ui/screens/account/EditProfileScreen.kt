package pt.isec.amov.safetysec.ui.screens.account

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.repository.AuthRepository
import pt.isec.amov.safetysec.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val repo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Estados dos campos
    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var cancelCode by remember { mutableStateOf("") }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    var showCurrentPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }
    var showConfirmPass by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }

    val msgSaved = stringResource(R.string.msg_saved_success)
    val msgErrorValidation = stringResource(R.string.profile_validation_error)
    val msgmissPass = stringResource(R.string.error_missing_current_password)
    val msgNewPassSame = stringResource(R.string.error_new_password_same_as_old)
    val msgConfirmPassMismatch = stringResource(R.string.error_confirm_password_mismatch)
    val msgPassTooShort = stringResource(R.string.error_password_too_short)
    val msgErrorProfile = stringResource(R.string.error_profile_update_prefix)



    LaunchedEffect(Unit) {
        if (user != null) {
            val fullData = repo.getUserData(user.uid)
            if (fullData != null) {
                cancelCode = fullData.cancelCode
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(scrollState))
        {

            // Campo Nome
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.full_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // Campo PIN (Cancel Code)
            OutlinedTextField(
                value = cancelCode,
                onValueChange = { if (it.length <= 4) cancelCode = it },
                label = { Text(stringResource(R.string.cancellation_code_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.cancellation_hint)) }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // --- SECÇÃO ALTERAR PASSWORD ---
            Text(stringResource(R.string.change_password_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // Campo Password
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text(stringResource(R.string.label_current_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showCurrentPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showCurrentPass = !showCurrentPass }) {
                        Icon(if (showCurrentPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.label_new_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showNewPass = !showNewPass }) {
                        Icon(if (showNewPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            // 3. Confirmar Nova Password
            OutlinedTextField(
                value = confirmNewPassword,
                onValueChange = { confirmNewPassword = it },
                label = { Text(stringResource(R.string.label_confirm_new_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showConfirmPass = !showConfirmPass }) {
                        Icon(if (showConfirmPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                    }
                }
            )

            Spacer(Modifier.height(32.dp))

            // Botão Guardar
            Button(
                onClick = {
                    if (user != null && name.isNotBlank() && cancelCode.length == 4) {
                        isLoading = true

                        if (newPassword.isNotBlank()) {
                            // 1. Verificar se preencheu a password atual
                            if (currentPassword.isBlank()) {
                                isLoading = false
                                Toast.makeText(context, msgmissPass, Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // 2.Validar se a nova é igual à antiga
                            if (newPassword == currentPassword) {
                                isLoading = false
                                Toast.makeText(context, msgNewPassSame, Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            // 3. Validar confirmação
                            if (newPassword != confirmNewPassword) {
                                isLoading = false
                                Toast.makeText(context, msgConfirmPassMismatch, Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // 4. Validar tamanho mínimo
                            if (newPassword.length < 6) {
                                isLoading = false
                                Toast.makeText(context, msgPassTooShort, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }
                        scope.launch {
                            val resultProfile = repo.updateUserProfile(user.uid, name, cancelCode)

                            if (resultProfile.isFailure) {
                                isLoading = false
                                Toast.makeText(context, msgErrorProfile + " ${resultProfile.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            if (newPassword.isNotBlank()) {
                                val resultPass = repo.updatePassword(newPassword)
                                if (resultPass.isFailure) {
                                    isLoading = false
                                    Toast.makeText(context, msgErrorProfile + " ${resultPass.exceptionOrNull()?.message}. Tente fazer login novamente.", Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                            }

                            isLoading = false
                            Toast.makeText(context, msgSaved, Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } else {
                        Toast.makeText(context, msgErrorValidation, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Row {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_changes))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}