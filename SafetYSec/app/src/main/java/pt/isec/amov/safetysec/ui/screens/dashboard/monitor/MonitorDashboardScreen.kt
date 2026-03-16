package pt.isec.amov.safetysec.ui.screens.dashboard.monitor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.model.SafetyAlert
import pt.isec.amov.safetysec.model.SafetyUser
import pt.isec.amov.safetysec.services.MonitorService
import pt.isec.amov.safetysec.ui.viewmodels.AssociationViewModel
import pt.isec.amov.safetysec.ui.viewmodels.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorDashboardScreen(
    navController: NavController,
    viewModel: AssociationViewModel = viewModel(),
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentUser = viewModel.getCurrentUser()

    // --- ESTADOS ---
    var protectedUsers by remember { mutableStateOf<List<SafetyUser>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var inputCode by remember { mutableStateOf("") }
    var userToRemove by remember { mutableStateOf<SafetyUser?>(null) }
    var videoUrlToPlay by remember { mutableStateOf<String?>(null) }
    var userForAlerts by remember { mutableStateOf<SafetyUser?>(null) }
    var latestCriticalAlert by remember { mutableStateOf<SafetyAlert?>(null) }
    var lastShownAlertId by remember { mutableStateOf("") }

    val msgRemoved = stringResource(R.string.msg_removed)
    val msgAssociated = stringResource(R.string.msg_associated)
    val msgCodeLen = stringResource(R.string.error_code_length)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(context, MonitorService::class.java)
            context.startForegroundService(intent)
        }
    }

    // INICIAR O SERVIÇO DE NOTIFICAÇÕES (FOREGROUND)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Pede permissão primeiro
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 12 ou inferior: Inicia direto
            val intent = Intent(context, MonitorService::class.java)
            context.startForegroundService(intent)
        }
    }

    // --- DADOS EM TEMPO REAL ---
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            dashboardViewModel.startListeningAlerts()

            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    val monitoringIds = snapshot?.get("monitoring") as? List<String> ?: emptyList()
                    if (monitoringIds.isNotEmpty()) {
                        db.collection("users").whereIn("id", monitoringIds)
                            .addSnapshotListener { usersSnap, _ ->
                                if (usersSnap != null) protectedUsers =
                                    usersSnap.toObjects(SafetyUser::class.java)
                            }
                    } else {
                        protectedUsers = emptyList()
                    }
                }
        }
    }

    LaunchedEffect(dashboardViewModel.alerts) {
        val newest = dashboardViewModel.alerts
            .filter { it.status == "ACTIVE" }
            .maxByOrNull { it.timestamp }

        if (newest != null) {
            val now = com.google.firebase.Timestamp.now().toDate().time
            val alertTime = newest.timestamp.toDate().time

            if (now - alertTime < 60 * 1000 && newest.id != lastShownAlertId) {
                latestCriticalAlert = newest
                lastShownAlertId = newest.id
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.monitor_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back_icon))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.errorMessage = null
                    showAddDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.desc_add_protected)) },
                text = { Text(stringResource(R.string.add_protected_title)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. STATS
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsCard(
                        stringResource(R.string.total_protected_stat),
                        protectedUsers.size.toString(),
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.primaryContainer
                    )
                    StatsCard(
                        stringResource(R.string.active_alerts_stat),
                        dashboardViewModel.alerts.size.toString(),
                        Modifier.weight(1f),
                        if (dashboardViewModel.alerts.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                    )
                }
            }

            // 2. LISTA PROTEGIDOS
            item {
                Text(
                    stringResource(R.string.my_protected_users),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (protectedUsers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_monitoring_active), textAlign = TextAlign.Center, color = Color.Gray)
                    }
                }
            } else {
                items(protectedUsers) { user ->
                    val userAlertsCount = dashboardViewModel.alerts.count { it.protectedId == user.id }
                    val statusText = if (userAlertsCount > 0) "${stringResource(R.string.status_in_alert)} ($userAlertsCount)" else stringResource(R.string.status_safe)
                    val isSafe = userAlertsCount == 0
                    val displayName = user.name.ifBlank { user.email }

                    ProtectedUserItem(
                        name = displayName,
                        status = statusText,
                        isSafe = isSafe,
                        onManageRules = { navController.navigate("manageRules/${user.id}") },
                        onRemove = { userToRemove = user },
                        onViewAlerts = { userForAlerts = user }
                    )
                }
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    // --- VIDEO PLAYER DIALOG
    if (videoUrlToPlay != null) {
        VideoPlayerDialog(
            url = videoUrlToPlay!!,
            onDismiss = { videoUrlToPlay = null }
        )
    }

    // --- REMOVE DIALOG ---
    if (userToRemove != null) {
        AlertDialog(
            onDismissRequest = { userToRemove = null },
            title = { Text(stringResource(R.string.remove_protected_title)) },
            text = { Text(stringResource(R.string.remove_protected_confirm, userToRemove?.name ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        userToRemove?.let { viewModel.removeAssociation(it.id) }
                        userToRemove = null
                        Toast.makeText(context, msgRemoved, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { userToRemove = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // --- ADD DIALOG ---
    if (showAddDialog) {
        LaunchedEffect(viewModel.errorMessage) {
            viewModel.errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; viewModel.errorMessage = null },
            title = { Text(stringResource(R.string.add_protected_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.enter_code_monitor_instruction))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { if (it.length <= 6) inputCode = it.uppercase() },
                        label = { Text(stringResource(R.string.code_hint)) },
                        placeholder = { Text("Ex: X9J2K1") },
                        singleLine = true,
                        isError = viewModel.errorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (viewModel.errorMessage != null) {
                        Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (viewModel.isLoading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputCode.length == 6) {
                            viewModel.associateMonitor(inputCode) {
                                showAddDialog = false
                                inputCode = ""
                                Toast.makeText(context, msgAssociated, Toast.LENGTH_SHORT).show()
                            }
                        } else { Toast.makeText(context, msgCodeLen, Toast.LENGTH_SHORT).show() }
                    },
                    enabled = !viewModel.isLoading
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; viewModel.errorMessage = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // --- POPUP AUTOMÁTICO DE ALERTA ---
    if (latestCriticalAlert != null) {
        AlertDialog(
            onDismissRequest = { latestCriticalAlert = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
            title = { Text(stringResource(R.string.popup_alert_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val typeText = when(latestCriticalAlert?.type.toString()) {
                        "FALL" -> stringResource(R.string.alert_type_fall)
                        "ACCIDENT" -> stringResource(R.string.alert_type_accident)
                        "PANIC_BUTTON" -> stringResource(R.string.alert_type_panic)
                        "INACTIVITY" -> stringResource(R.string.alert_type_inactivity)
                        "SPEED" -> stringResource(R.string.alert_type_speed)
                        "GEOFENCING" -> stringResource(R.string.alert_type_geofencing)
                        else -> latestCriticalAlert?.type.toString()
                    }
                    Text(stringResource(R.string.label_type, typeText), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.user_info, latestCriticalAlert?.protectedName ?: stringResource(R.string.default_user)), style = MaterialTheme.typography.bodyLarge)
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val timeStr = latestCriticalAlert?.timestamp?.toDate()?.let { dateFormat.format(it) } ?: stringResource(R.string.now_label)

                    Text(stringResource(R.string.label_time, timeStr))

                    val location = latestCriticalAlert?.coordinates ?: "N/A"
                    Text(stringResource(R.string.location_label, location))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = latestCriticalAlert?.videoUrl
                        if (!url.isNullOrBlank()) { videoUrlToPlay = url }
                        userForAlerts = protectedUsers.find { it.id == latestCriticalAlert?.protectedId }
                        latestCriticalAlert = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_check_alert).uppercase())
                }
            },
            dismissButton = {
                TextButton(onClick = { latestCriticalAlert = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_ignore_alert)) }
            }
        )
    }

    // --- DIALOG DE ALERTAS DO UTILIZADOR ---
    if (userForAlerts != null) {
        val specificAlerts = dashboardViewModel.alerts.filter { it.protectedId == userForAlerts!!.id }
        AlertDialog(
            onDismissRequest = { userForAlerts = null },
            title = { Text(stringResource(R.string.user_alerts_title, userForAlerts!!.name)) },
            text = {
                if (specificAlerts.isEmpty()) {
                    Text(stringResource(R.string.no_active_alerts_user), fontStyle = FontStyle.Italic)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(specificAlerts) { alert ->
                            AlertItem(
                                alert = alert,
                                onPlayVideo = { videoUrlToPlay = it },
                                onResolve = { dashboardViewModel.resolveAlert(alert.id) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { userForAlerts = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}

// --- COMPONENTE PLAYER EXO ---
@Composable
fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Configurar ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Botão de fechar
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(stringResource(R.string.close), color = Color.White)
                }
            }
        }
    }
}


@Composable
fun AlertItem(alert: SafetyAlert, onPlayVideo: (String) -> Unit, onResolve: (() -> Unit)? = null) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateStr = alert.timestamp.toDate().let { dateFormat.format(it) } ?: stringResource(R.string.now_label)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${stringResource(R.string.sos)}: ${alert.protectedName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Type: ${alert.type}", style = MaterialTheme.typography.bodySmall)
                    Text("Time: $dateStr", style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.location_label, alert.coordinates), style = MaterialTheme.typography.labelSmall)
                    Text("Time: $dateStr", style = MaterialTheme.typography.labelSmall)
                }
                if (!alert.videoUrl.isNullOrBlank()) {
                    IconButton(onClick = { onPlayVideo(alert.videoUrl) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.desc_play_video), tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                if (onResolve != null) {
                    IconButton(onClick = onResolve) {
                        Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.desc_resolve_alert), tint = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(title: String, value: String, modifier: Modifier, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ProtectedUserItem(name: String, status: String, isSafe: Boolean, onManageRules: () -> Unit, onRemove: () -> Unit, onViewAlerts: () -> Unit) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        ListItem(
            headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(status, color = if (isSafe) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = {
                Row {
                    IconButton(onClick = onViewAlerts) {
                        Icon(if (!isSafe) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone, contentDescription = null, tint = if (!isSafe) MaterialTheme.colorScheme.error else LocalContentColor.current)
                    }
                    IconButton(onClick = onManageRules) { Icon(Icons.Default.Settings, contentDescription = null) }
                    IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}