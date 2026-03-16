package pt.isec.amov.safetysec.ui.screens.dashboard.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.model.SafetyAlert
import pt.isec.amov.safetysec.ui.viewmodels.AssociationViewModel
import pt.isec.amov.safetysec.ui.viewmodels.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    navController: NavController,
    associationViewModel: AssociationViewModel = viewModel(),
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    val user = associationViewModel.getCurrentUser()

    LaunchedEffect(user) {
        if (user != null) {
            dashboardViewModel.fetchAlertHistory(user.uid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alert_history)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val history = dashboardViewModel.alertHistory

        if (history.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_alert_history), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { alert ->
                    HistoryItem(alert)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(alert: SafetyAlert) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = alert.timestamp.toDate().let { dateFormat.format(it) } ?: "N/A"

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        ListItem(
            headlineContent = { Text(alert.type.name, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.date_label, dateStr))
                    Text(stringResource(R.string.location_label, alert.coordinates), style = MaterialTheme.typography.labelSmall)
                }
            },
            leadingContent = {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}