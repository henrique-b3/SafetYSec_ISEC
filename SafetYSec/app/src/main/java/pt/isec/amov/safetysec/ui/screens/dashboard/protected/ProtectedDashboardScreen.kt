package pt.isec.amov.safetysec.ui.screens.dashboard.protected

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.services.SafetyMonitoringService
import pt.isec.amov.safetysec.ui.viewmodels.AssociationViewModel
import pt.isec.amov.safetysec.ui.viewmodels.DashboardViewModel
import android.provider.Settings
import android.net.Uri
import pt.isec.amov.safetysec.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedDashboardScreen(
    navController: NavController,
    viewModel: AssociationViewModel = viewModel(),
) {
    val context = LocalContext.current

    var showCodeDialog by remember { mutableStateOf(false) }

    val msgLocation = stringResource(R.string.error_location_permissions)
    val msgLocBgActive = stringResource(R.string.loc_bg_active)
    val msgGeoLimit = stringResource(R.string.geofencing_limited)
    val msgCopied = stringResource(R.string.msg_copied)

    val manufacturer = Build.MANUFACTURER.lowercase()
    val isXiaomiOrSamsung = manufacturer.contains("xiaomi") || manufacturer.contains("samsung")

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, msgLocBgActive, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, msgGeoLimit, Toast.LENGTH_LONG).show()
        }
    }

    // Permissões e Inicialização
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (locationGranted) {
            val intent = Intent(context, SafetyMonitoringService::class.java)
            context.startForegroundService(intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasBackground = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasBackground) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            Toast.makeText(context, msgLocation, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
        viewModel.fetchMyMonitors()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.protected_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back_icon)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.tap_sos),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(24.dp))

                SosCircularButton(onClick = {
                    // LÓGICA DE SOS AGORA VAI PARA O SERVIÇO
                    val allMonitors = viewModel.myMonitors.map { it.id } as ArrayList<String>

                    val intent = Intent(context, SafetyMonitoringService::class.java).apply {
                        action = SafetyMonitoringService.ACTION_TRIGGER_SOS
                        putStringArrayListExtra(
                            SafetyMonitoringService.EXTRA_TARGET_MONITORS,
                            allMonitors
                        )
                    }
                    context.startForegroundService(intent)
                })

                Spacer(Modifier.height(48.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ActionCard(
                            Icons.Default.PersonAdd,
                            stringResource(R.string.associate_monitor)
                        ) {
                            viewModel.generateAssociationCode()
                            showCodeDialog = true
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ActionCard(
                            Icons.Default.Group,
                            stringResource(R.string.my_monitors_title)
                        ) { navController.navigate("myMonitors") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ActionCard(
                            Icons.Default.History,
                            stringResource(R.string.alert_history)
                        ) { navController.navigate("alertHistory") }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(20.dp))

                val missingOverlay = !PermissionUtils.hasOverlayPermission(context)

                val missingBattery = !PermissionUtils.isIgnoringBatteryOptimizations(context)

                if (isXiaomiOrSamsung || missingOverlay || missingBattery) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.config_required_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.config_required_desc),
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (!PermissionUtils.hasOverlayPermission(context)) {
                                        PermissionUtils.requestOverlayPermission(context)
                                    } else if (!PermissionUtils.isIgnoringBatteryOptimizations(
                                            context
                                        )
                                    ) {
                                        PermissionUtils.requestBatteryExemption(context)
                                    } else {
                                        PermissionUtils.openSpecificPermissions(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                    contentColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.btn_resolve_permissions))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showCodeDialog) {
        Dialog(onDismissRequest = { showCodeDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.your_code),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(16.dp))
                    if (viewModel.isGenerating) CircularProgressIndicator()
                    else {
                        val code = viewModel.generatedCode ?: stringResource(R.string.error_caps)
                        val clipboard = LocalClipboardManager.current
                        Text(
                            text = code,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    Color.LightGray.copy(0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp)
                                .clickable {
                                    clipboard.setText(AnnotatedString(code)); Toast.makeText(
                                    context,
                                    msgCopied,
                                    Toast.LENGTH_SHORT
                                ).show()
                                })
                    }
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = {
                        showCodeDialog = false
                    }) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}


@Composable
fun SosCircularButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.9f else 1f, label = "scale")
    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Red)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White)
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = stringResource(R.string.desc_sos_icon),
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
            Text(
                stringResource(R.string.sos),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ActionCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .height(110.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
    }
}