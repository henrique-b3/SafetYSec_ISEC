package pt.isec.amov.safetysec.ui.screens.alert

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.model.RuleType
import pt.isec.amov.safetysec.repository.AuthRepository
import pt.isec.amov.safetysec.services.SafetyMonitoringService
import pt.isec.amov.safetysec.ui.viewmodels.DashboardViewModel
import pt.isec.amov.safetysec.utils.VideoManager
import android.view.WindowManager
import androidx.activity.compose.setContent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.NotificationManager
import android.util.Log
import androidx.annotation.RequiresPermission

class AlertActivity : ComponentActivity() {

    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "AlertActivity"
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        Log.d(TAG, "AlertActivity criada")

        // 1. Cancelar a notificação imediatamente
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(SafetyMonitoringService.NOTIFICATION_ID_ALERT)

        // 2. Configurar a janela para aparecer sobre a tela de bloqueio
        setupWindowForLockScreen()

        // 3. Configurar som e vibração
        startAlertSoundAndVibration()

        // 4. Recuperar dados do intent
        val typeName = intent.getStringExtra(SafetyMonitoringService.EXTRA_RULE_TYPE)
        val targetMonitorIds = intent.getStringArrayListExtra(SafetyMonitoringService.EXTRA_TARGET_MONITORS) ?: arrayListOf()
        val currentAlertType = try {
            if (typeName != null) RuleType.valueOf(typeName) else RuleType.FALL
        } catch (_: Exception) { RuleType.FALL }

        // 5. Configurar a UI
        setContent {
            AlertScreenContent(
                currentAlertType = currentAlertType,
                targetMonitorIds = targetMonitorIds,
                onClose = {
                    stopAlertSoundAndVibration()
                    finishAndRemoveTask()
                    // Não use animações
                    overridePendingTransition(0, 0)
                }
            )
        }
    }

    private fun setupWindowForLockScreen() {
        // Método mais confiável para todas as versões do Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Para Android 8.1+
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            // Para versões anteriores
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }

        // Configurações adicionais da janela
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        // Para dispositivos Samsung e outros fabricantes
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun startAlertSoundAndVibration() {
        try {
            /*val alertSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            ringtone = RingtoneManager.getRingtone(applicationContext, alertSound).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    streamType = AudioManager.STREAM_ALARM
                }
                play()
            }*/

            // Configurar vibração
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                val vibrationPattern = longArrayOf(0, 1000, 500, 1000)

                vibrator?.vibrate(
                    VibrationEffect.createWaveform(vibrationPattern, 0)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar som/vibração: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun stopAlertSoundAndVibration() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onPause() {
        super.onPause()
        // Manter a Activity viva mesmo quando em pausa
        if (!isFinishing) {
            Log.d(TAG, "AlertActivity em pausa, mantendo recursos")
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun onDestroy() {
        super.onDestroy()
        stopAlertSoundAndVibration()
        Log.d(TAG, "AlertActivity destruída")
    }
}


@Composable
fun AlertScreenContent(
    currentAlertType: RuleType,
    targetMonitorIds: List<String>,
    onClose: () -> Unit,
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val authRepo = remember { AuthRepository() }
    val videoManager = remember { VideoManager(context) }
    val scope = rememberCoroutineScope()

    var isAlertActive by remember { mutableStateOf(true) }
    var alertSent by remember { mutableStateOf(false) }
    var alertIdCreated by remember { mutableStateOf<String?>(null) }
    var timeLeft by remember { mutableIntStateOf(10) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    // Dialog PIN
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var userSavedPin by remember { mutableStateOf("") }

    val msgVideoSent = stringResource(R.string.msg_video_sent)
    val msgAlertCancelled = stringResource(R.string.msg_alert_cancelled)
    val msgPinIncorrect = stringResource(R.string.msg_pin_incorrect)
    val msgProtected = stringResource(R.string.protected_role)

    // Obter Utilizador Atual
    LaunchedEffect(Unit) {
        val currentUser = authRepo.getCurrentUser()
        if (currentUser != null) {
            val userFull = authRepo.getUserData(currentUser.uid)
            if (userFull != null) userSavedPin = userFull.cancelCode
        }
    }

    // --- LOGICA DE TIMER E ENVIO ---
    LaunchedEffect(isAlertActive) {
        if (isAlertActive && !alertSent) {
            timeLeft = 10
            while (timeLeft > 0 && isAlertActive) {
                delay(1000L)
                timeLeft--
            }
            if (timeLeft == 0 && isAlertActive) {
                val currentUser = authRepo.getCurrentUser()
                if (currentUser != null) {
                    if (targetMonitorIds.isNotEmpty()) {
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                        // Função auxiliar para enviar
                        fun performSend(loc: String) {
                            dashboardViewModel.sendAlert(
                                protectedId = currentUser.uid,
                                protectedName = currentUser.displayName ?: msgProtected,
                                location = loc,
                                type = currentAlertType,
                                videoUrl = "",
                                targetMonitorIds = targetMonitorIds
                            ) { newAlertId ->
                                alertIdCreated = newAlertId
                                alertSent = true
                            }
                        }

                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener { location ->
                                    val realLocation = if (location != null) "${location.latitude},${location.longitude}" else "0.0,0.0"
                                    performSend(realLocation)
                                }
                                .addOnFailureListener { performSend("0.0,0.0") }
                        } else {
                            performSend("0.0,0.0")
                        }
                    } else {
                        Toast.makeText(context, "Sem monitores definidos", Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                }
            }
        }
    }

    // --- UI (Exatamente igual ao Overlay da Dashboard) ---
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Red).clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.desc_warning_icon), modifier = Modifier.size(60.dp), tint = Color.White)
            Spacer(Modifier.height(16.dp))

            if (!alertSent) {
                val title = if (currentAlertType == RuleType.FALL) stringResource(R.string.fall_detected_title) else stringResource(R.string.sos_request_title)
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sending_in), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text("$timeLeft", color = Color.White, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { pinInput = ""; showPinDialog = true }, // Pede PIN para cancelar mesmo antes de enviar
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) { Text(stringResource(R.string.cancel_button_caps), color = Color.Red, fontWeight = FontWeight.Bold) }
            } else {
                // Ecrã de Gravação
                Text(stringResource(R.string.alert_sent_msg), color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LaunchedEffect(Unit) {
                    recordingSeconds = 0
                    while (recordingSeconds < 30 && isAlertActive) {
                        delay(1000)
                        recordingSeconds++
                    }
                    // Se chegar ao fim sem cancelar, termina
                    if(isAlertActive) {
                        Toast.makeText(context, msgVideoSent, Toast.LENGTH_LONG).show()
                        onClose()
                    }
                }
                Text(stringResource(R.string.recording_status, recordingSeconds), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                if (alertIdCreated != null) {
                    Box(modifier = Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, Color.White, RoundedCornerShape(16.dp))) {
                        val cameraDesc = stringResource(R.string.desc_camera_preview)
                        AndroidView(factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                contentDescription = cameraDesc
                                keepScreenOn = true
                            }
                            videoManager.startRecording(lifecycleOwner, previewView, alertIdCreated ?: "") {
                                scope.launch {
                                    Toast.makeText(context, msgVideoSent, Toast.LENGTH_LONG).show()
                                    onClose()
                                }
                            }
                            previewView
                        }, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { pinInput = ""; showPinDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) { Text(stringResource(R.string.cancel_alert_pin_btn), color = Color.Red, fontWeight = FontWeight.Bold) }
            }
        }
    }

    // --- DIALOGS (PIN) ---
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(R.string.cancel_alert_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.cancel_alert_instruction))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput == userSavedPin) {
                            videoManager.cancelRecording()
                            if (alertIdCreated != null) dashboardViewModel.cancelAlertStatus(alertIdCreated!!)
                            isAlertActive = false
                            Toast.makeText(context, msgAlertCancelled, Toast.LENGTH_SHORT).show()
                            onClose() // Fecha a Activity
                        } else { Toast.makeText(context, msgPinIncorrect, Toast.LENGTH_SHORT).show() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text(stringResource(R.string.back)) } }
        )
    }
}