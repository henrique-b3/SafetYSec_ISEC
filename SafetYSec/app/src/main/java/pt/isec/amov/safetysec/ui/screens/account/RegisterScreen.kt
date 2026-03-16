package pt.isec.amov.safetysec.ui.screens.account

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.ui.viewmodels.AuthViewModel
import pt.isec.amov.safetysec.ui.viewmodels.LoginState

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    navController: NavController
) {
    val context = LocalContext.current

    // --- ESTADOS LOCAIS ---
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var cancelCode by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val loginState by viewModel.loginState.collectAsState()

    val errorResId = viewModel.errorResId
    val isLoading = viewModel.isLoading

    val errorFill = stringResource(R.string.error_fill_all_fields)
    val errorMatch = stringResource(R.string.error_passwords_do_not_match)
    val errorWeak = stringResource(R.string.error_weak_password)
    val errorPin = stringResource(R.string.error_pin_length)
    val msgSuccess = stringResource(R.string.msg_account_created)

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            Toast.makeText(context, msgSuccess, Toast.LENGTH_SHORT).show()

            viewModel.logout()
            navController.navigate("login") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    LaunchedEffect(errorResId) {
        errorResId?.let { id ->
            Toast.makeText(context, id, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_safetysec),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .heightIn(max = 180.dp)
                .aspectRatio(1f)
        )

        Text(text = stringResource(R.string.create_account), style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        // Campo Nome
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.full_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        // Campo Confirmar Password
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text(stringResource(R.string.confirm_password)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(imageVector = image, contentDescription = null)
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Campo PIN (Cancel Code)
        OutlinedTextField(
            value = cancelCode,
            onValueChange = { if (it.length <= 4) cancelCode = it },
            label = { Text(stringResource(R.string.cancellation_code_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Botão Registar
        Button(
            onClick = {
                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                    Toast.makeText(context, errorFill, Toast.LENGTH_SHORT).show()
                } else if (password != confirmPassword) {
                    Toast.makeText(context, errorMatch, Toast.LENGTH_SHORT).show()
                } else if (password.length < 6) {
                    Toast.makeText(context, errorWeak, Toast.LENGTH_SHORT).show()
                } else if (cancelCode.length != 4) {
                    Toast.makeText(context, errorPin, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.register(name, email, password, cancelCode)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.register))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botão Voltar ao Login
        TextButton(onClick = { navController.popBackStack() }) {
            Text(stringResource(R.string.have_account_signin))
        }
    }
}