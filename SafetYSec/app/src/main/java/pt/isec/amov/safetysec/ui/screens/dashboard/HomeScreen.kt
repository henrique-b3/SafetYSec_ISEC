package pt.isec.amov.safetysec.ui.screens.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.ui.components.LanguageSelector
import pt.isec.amov.safetysec.ui.viewmodels.AuthViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import pt.isec.amov.safetysec.services.MonitorService
import pt.isec.amov.safetysec.services.SafetyMonitoringService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    val user = authViewModel.getLoggedInUser()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SafetySec")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        authViewModel.logout()

                        val stopProtectedIntent = Intent(context, SafetyMonitoringService::class.java)
                        context.stopService(stopProtectedIntent)

                        val stopMonitorIntent = Intent(context, MonitorService::class.java)
                        context.stopService(stopMonitorIntent)

                        navController.navigate("login") {
                            popUpTo(0)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.desc_logout_icon))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            if (user != null) {
                Text(
                    stringResource(R.string.welcome) + " ${user.displayName ?: stringResource(R.string.default_user)}",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Image(
                painter = painterResource(id = R.drawable.ic_safetysec),
                contentDescription = stringResource(R.string.desc_logo),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .heightIn(max = 180.dp)
                    .aspectRatio(1f)
            )

            Spacer(Modifier.height(10.dp))

            // Botão Modo Monitor
            ProfileCard(
                title = stringResource(R.string.mode_monitor),
                subtitle = stringResource(R.string.monitor_subtitle),
                icon = Icons.Default.Visibility,
                iconDesc = stringResource(R.string.desc_monitor_mode_icon),
                onClick = { navController.navigate("monitorDashboard") }
            )

            Spacer(Modifier.height(24.dp))

            // Botão Modo Protegido
            ProfileCard(
                title = stringResource(R.string.mode_protected),
                subtitle = stringResource(R.string.protected_subtitle),
                icon = Icons.Default.Security,
                iconDesc = stringResource(R.string.desc_protected_mode_icon),
                onClick = { navController.navigate("protectedDashboard") }
            )

            Spacer(Modifier.height(45.dp))

            SettingsCard(
                title = stringResource(R.string.edit_profile),
                icon = Icons.Default.Settings,
                onClick = { navController.navigate("editProfile") }
            )

            Spacer(Modifier.height(45.dp))

            LanguageSelector(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
fun ProfileCard(title: String, subtitle: String, icon: ImageVector, iconDesc: String,onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDesc,
                modifier = Modifier.size(35.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.desc_settings_icon),
                modifier = Modifier.size(35.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}