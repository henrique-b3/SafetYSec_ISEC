package pt.isec.amov.safetysec.ui.screens.dashboard.protected

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.ui.viewmodels.AssociationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMonitorsScreen(
    navController: NavController,
    viewModel: AssociationViewModel = viewModel()
) {
    val context = LocalContext.current
    val msgRemoved = stringResource(R.string.msg_monitor_removed)
    val currentUser = viewModel.getCurrentUser()

    LaunchedEffect(Unit) {
        viewModel.fetchMyMonitors()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_monitors_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val monitors = viewModel.myMonitors

        if (monitors.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_monitors_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(stringResource(R.string.people_access_location), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                }

                items(monitors) { monitor ->
                    Card(elevation = CardDefaults.cardElevation(2.dp)) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = stringResource(R.string.desc_person_icon), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(monitor.name, fontWeight = FontWeight.Bold)
                                Text(monitor.email, style = MaterialTheme.typography.bodySmall)
                            }

                            // BOTÃO REGRAS (Engrenagem)
                            if (currentUser != null) {
                                IconButton(onClick = {
                                    // Navega para gerir regras DESTE monitor específico
                                    navController.navigate("manageRules/${currentUser.uid}/${monitor.id}")
                                }) {
                                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.desc_rules_icon), tint = MaterialTheme.colorScheme.secondary)
                                }
                            }

                            // BOTÃO REMOVER
                            IconButton(onClick = {
                                viewModel.removeAssociation(monitor.id) {
                                    Toast.makeText(context, msgRemoved, Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}