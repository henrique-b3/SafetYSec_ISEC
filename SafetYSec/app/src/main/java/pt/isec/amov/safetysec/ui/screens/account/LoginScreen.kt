package pt.isec.amov.safetysec.ui.screens.account

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import pt.isec.amov.safetysec.repository.AuthRepository
import pt.isec.amov.safetysec.ui.viewmodels.AuthViewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.ui.components.LanguageSelector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import pt.isec.amov.safetysec.ui.viewmodels.LoginState
import androidx.core.content.edit

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current

    // --- ESTADOS LOCAIS ---
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Dialog de recuperação
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    val authRepo = remember { AuthRepository() }

    // Observar estado do ViewModel
    val loginState by viewModel.loginState.collectAsState()
    val errorResId = viewModel.errorResId
    val isLoading = viewModel.isLoading

    val scrollState = rememberScrollState()
    val errorFillFields = stringResource(R.string.error_fill_all_fields)

    // Aceder às preferências
    val prefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.MfaRequired) {
            prefs.edit { putBoolean("mfa_pending", true) }
        }

        if (loginState is LoginState.Success) {
            prefs.edit { putBoolean("mfa_pending", false) }

            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
            }
        }

        if (loginState is LoginState.Error) {
            prefs.edit { putBoolean("mfa_pending", false) }

            val errorId = (loginState as LoginState.Error).errorId
            Toast.makeText(context, errorId, Toast.LENGTH_LONG).show()
        }
    }

    // Erros genéricos
    LaunchedEffect(errorResId) {
        errorResId?.let { id ->
            Toast.makeText(context, id, Toast.LENGTH_LONG).show()
        }
    }

    if (loginState is LoginState.MfaRequired) {
        val finalErrorMsg = if (viewModel.mfaErrorResId != null) {
            stringResource(viewModel.mfaErrorResId!!)
        } else if (errorResId != null) {
            stringResource(errorResId)
        } else {
            null
        }

        MfaVerificationScreen(
            email = email,
            isLoading = isLoading,
            errorMessage = finalErrorMsg,
            onVerifyClick = { code ->
                viewModel.verifyMfa(code)
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_safetysec),
                contentDescription = "Logo SafetySec",
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .heightIn(max = 180.dp)
                    .aspectRatio(1f)
            )

            Text(text = stringResource(R.string.logintxt), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(48.dp))

            // Campo Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Campo Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible)
                        stringResource(R.string.desc_hide_password)
                    else
                        stringResource(R.string.desc_show_password)

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                },
                singleLine = true
            )

            // Botão Esqueci a Palavra-passe
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { showResetDialog = true }) {
                    Text(text = stringResource(R.string.forgot_password), style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botão Login
            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        viewModel.login(email, password)
                    } else {
                        Toast.makeText(context, errorFillFields, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.login))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botão Criar Conta
            OutlinedButton(
                onClick = { navController.navigate("register") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.create_account))
            }

            Spacer(modifier = Modifier.height(16.dp))

            LanguageSelector(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    }

    // Dialog de Recuperação de Password
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.recover_access)) },
            text = {
                Column {
                    Text(stringResource(R.string.recover_instruction))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(stringResource(R.string.email)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (resetEmail.isNotBlank()) {
                        authRepo.sendPasswordResetEmail(resetEmail) { result ->
                            result.onSuccess { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                            result.onFailure { err -> Toast.makeText(context, "Erro: ${err.message}", Toast.LENGTH_LONG).show() }
                        }
                        showResetDialog = false
                    }
                }) { Text(stringResource(R.string.send)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}