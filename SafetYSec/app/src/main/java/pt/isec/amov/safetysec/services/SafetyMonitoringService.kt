package pt.isec.amov.safetysec.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.model.RuleType
import pt.isec.amov.safetysec.model.SafetyRule
import pt.isec.amov.safetysec.ui.screens.alert.AlertActivity
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

class SafetyMonitoringService : Service(), SensorEventListener{

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var rulesListener: ListenerRegistration? = null

    // Lista de regras carregadas
    private var activeRules: List<SafetyRule> = emptyList()
    private var areRulesLoaded = false

    private var lastMovementTime: Long = System.currentTimeMillis()
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private var inactivityRunnable: Runnable? = null

    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN_MS = 10000

    companion object {
        const val CHANNEL_ID = "SafetySecChannel_Critical_v5"
        // ... (outras constantes iguais)
        const val ACTION_TRIGGER_SOS = "pt.isec.amov.safetysec.ACTION_TRIGGER_SOS"
        const val EXTRA_RULE_TYPE = "EXTRA_RULE_TYPE"
        const val EXTRA_TARGET_MONITORS = "EXTRA_TARGET_MONITORS"
        const val TAG = "SafetyService"
        const val NOTIFICATION_ID_ALERT = 999

    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification()) // Notificação de background normal

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processLocation(location)
                }
            }
        }
        startLocationUpdates()
        startRulesListener()
        startInactivityCheck()
    }

    // NOVO: Lidar com comandos manuais (SOS)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_SOS) {
            val monitors = intent.getStringArrayListExtra(EXTRA_TARGET_MONITORS)
            if (monitors != null && monitors.isNotEmpty()) {
                triggerAlert(RuleType.PANIC_BUTTON, monitors)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (checkPermissions()) {
            try {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                    .setMinUpdateIntervalMillis(10000)
                    .build()

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro FusedLocation: ${e.message}")
            }
        }
    }

    private fun startRulesListener() {
        val user = auth.currentUser ?: return
        rulesListener = db.collection("users").document(user.uid)
            .collection("rules")
            .addSnapshotListener { snapshot, e ->
                if (snapshot != null) {
                    try {
                        val allRules = snapshot.toObjects(SafetyRule::class.java)
                        activeRules = allRules.filter { it.isActive }
                        areRulesLoaded = true
                    } catch (ex: Exception) {
                        Log.e(TAG, "Erro regras: ${ex.message}")
                    }
                }
            }
    }

    private fun isRuleActiveNow(rule: SafetyRule): Boolean {
        if (!rule.isActive) return false
        val scheduleEnabled = rule.parameters["scheduleEnabled"] == "true"
        if (!scheduleEnabled) return true

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val allowedDays = rule.parameters["days"] ?: ""
        if (allowedDays.isNotBlank()) {
            val daysList = allowedDays.split(",").mapNotNull { it.toIntOrNull() }
            if (!daysList.contains(currentDay)) return false
        }

        val startTimeStr = rule.parameters["startTime"] ?: "00:00"
        val endTimeStr = rule.parameters["endTime"] ?: "23:59"
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTimeVal = currentHour * 60 + currentMinute
        val (startH, startM) = parseTime(startTimeStr)
        val (endH, endM) = parseTime(endTimeStr)
        val startTimeVal = startH * 60 + startM
        val endTimeVal = endH * 60 + endM

        return if (startTimeVal <= endTimeVal) {
            currentTimeVal in startTimeVal..endTimeVal
        } else {
            currentTimeVal !in (endTimeVal + 1)..<startTimeVal
        }
    }

    private fun parseTime(timeStr: String): Pair<Int, Int> {
        val parts = timeStr.split(":")
        if (parts.size == 2) {
            return Pair(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
        }
        return Pair(0, 0)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                val gForce = sqrt((x * x + y * y + z * z).toDouble()) / 9.81

                if (abs(gForce - 1.0) > 0.1) lastMovementTime = System.currentTimeMillis()

                if (gForce > 5.0) {
                    val timeDiff = System.currentTimeMillis() - lastAlertTime
                    if (timeDiff < ALERT_COOLDOWN_MS) return
                    val monitors = getMonitorsForRule(RuleType.FALL)
                    if (monitors.isNotEmpty()) {
                        Log.i(TAG, "IMPACTO ($gForce G) -> ALERTA")
                        triggerAlert(RuleType.FALL, monitors)
                    }
                }
                if (gForce > 10.0) {
                    val timeDiff = System.currentTimeMillis() - lastAlertTime
                    if (timeDiff > ALERT_COOLDOWN_MS) {
                        val monitors = getMonitorsForRule(RuleType.ACCIDENT)
                        if (monitors.isNotEmpty()) {
                            triggerAlert(RuleType.ACCIDENT, monitors)
                        }
                    }
                }
            }
        }
    }

    private fun getMonitorsForRule(type: RuleType): ArrayList<String> {
        if (!areRulesLoaded) return ArrayList()
        val targetMonitors = ArrayList<String>()
        activeRules.forEach { rule ->
            if (rule.type == type && rule.isActive && isRuleActiveNow(rule)) {
                if (rule.monitorId.isNotBlank()) targetMonitors.add(rule.monitorId)
            }
        }
        return ArrayList(targetMonitors.distinct())
    }

    private fun triggerAlert(type: RuleType, targetMonitors: ArrayList<String>) {
        lastAlertTime = System.currentTimeMillis()

        val fullScreenIntent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra(EXTRA_RULE_TYPE, type.name)
            putStringArrayListExtra(EXTRA_TARGET_MONITORS, targetMonitors)
        }

        val isAndroid10Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val hasOverlayPermission = if (isAndroid10Plus) Settings.canDrawOverlays(this) else true

        if (!isAndroid10Plus || hasOverlayPermission) {
            try {
                startActivity(fullScreenIntent)
                Log.d(TAG, "Activity iniciada diretamente (Legacy ou Overlay Permitido)")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao iniciar direto, tentando notificação: ${e.message}")
            }
        }

        Log.d(TAG, "Usando FullScreenIntent Notification (Padrão Android 10+)")

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_ALERT,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createHighPriorityChannel()

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ALERTA DE SEGURANÇA!")
            .setContentText("Toque para ver detalhes")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setLights(Color.RED, 3000, 3000)

        // .setSound(...)

        val notification = notificationBuilder.build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            startForeground(NOTIFICATION_ID_ALERT, notification)
        } catch (e: Exception) {
            notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
        }
    }

    private fun createHighPriorityChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Canal de Alertas Críticos"
            val descriptionText = "Notificações de segurança em tela cheia"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun processLocation(location: Location) {
        if (!areRulesLoaded) return

        val timeDiff = System.currentTimeMillis() - lastAlertTime
        if (timeDiff < ALERT_COOLDOWN_MS) return

        val speedRules = activeRules.filter { it.type == RuleType.SPEED && isRuleActiveNow(it) }
        val speedTriggeredMonitors = ArrayList<String>()
        speedRules.forEach { rule ->
            val maxSpeed = rule.parameters["value"]?.replace(",", ".")?.toFloatOrNull() ?: 100f
            if ((location.speed * 3.6f) > maxSpeed) {
                if(rule.monitorId.isNotBlank()) speedTriggeredMonitors.add(rule.monitorId)
            }
        }
        if (speedTriggeredMonitors.isNotEmpty()) {

            triggerAlert(RuleType.SPEED, ArrayList(speedTriggeredMonitors.distinct()))
            return
        }

        val geoRules = activeRules.filter { it.type == RuleType.GEOFENCING && isRuleActiveNow(it) }
        val geoTriggeredMonitors = ArrayList<String>()
        geoRules.forEach { rule ->
            val paramLat = rule.parameters["latitude"]?.replace(",", ".")
            val paramLon = rule.parameters["longitude"]?.replace(",", ".")
            val paramRad = rule.parameters["radius"]?.replace(",", ".")


            val centerLat = paramLat?.toDoubleOrNull()
            val centerLon = paramLon?.toDoubleOrNull()
            val radius = paramRad?.toFloatOrNull() ?: 500f

            if (centerLat != null && centerLon != null) {
                val results = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, centerLat, centerLon, results)
                val distanceInMeters = results[0]
                if (distanceInMeters > radius) {
                    if (rule.monitorId.isNotBlank()) {
                        geoTriggeredMonitors.add(rule.monitorId)
                    }
                }
            }
        }
        if (geoTriggeredMonitors.isNotEmpty()) {
            triggerAlert(RuleType.GEOFENCING, ArrayList(geoTriggeredMonitors.distinct()))
        }
    }

    private fun startInactivityCheck() {
        inactivityRunnable = object : Runnable {
            override fun run() {
                val inactiveTime = System.currentTimeMillis() - lastMovementTime
                val triggeredMonitors = ArrayList<String>()
                activeRules.filter { it.type == RuleType.INACTIVITY && isRuleActiveNow(it) }.forEach { rule ->
                    val maxDur = (rule.parameters["duration"]?.toLongOrNull() ?: 60L) * 60 * 1000
                    if (inactiveTime > maxDur) {
                        if(rule.monitorId.isNotBlank()) triggeredMonitors.add(rule.monitorId)
                    }
                }
                if (triggeredMonitors.isNotEmpty()) {
                    lastMovementTime = System.currentTimeMillis()
                    val timeDiff = System.currentTimeMillis() - lastAlertTime
                    if (timeDiff > ALERT_COOLDOWN_MS) {
                        triggerAlert(RuleType.INACTIVITY, ArrayList(triggeredMonitors.distinct()))
                    }
                }
                inactivityHandler.postDelayed(this, 30000L)
            }
        }
        inactivityHandler.post(inactivityRunnable!!)
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        rulesListener?.remove()
        inactivityRunnable?.let { inactivityHandler.removeCallbacks(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Apagar canal antigo se existir para limpar definições
            val manager = getSystemService(NotificationManager::class.java)
            try { manager.deleteNotificationChannel("SafetySecChannel_HighPriority_v3") } catch (_: Exception){}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas Críticos",
                NotificationManager.IMPORTANCE_HIGH // MAX não existe em canais, usa-se HIGH + priority na notificação
            ).apply {
                description = "Alertas de ecrã cheio"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setBypassDnd(true) // Tenta ignorar "Não Incomodar"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafetySec Ativo")
            .setContentText("A monitorizar a sua segurança...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}