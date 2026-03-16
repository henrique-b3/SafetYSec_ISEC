package pt.isec.amov.safetysec.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import pt.isec.amov.safetysec.MainActivity
import pt.isec.amov.safetysec.R

class MonitorService : Service() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var alertListener: ListenerRegistration? = null

    companion object {
        const val CHANNEL_ID = "MonitorChannel"
        const val NOTIFICATION_ID = 200
        const val TAG = "MonitorService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Inicia o serviço em Foreground para o Android não o matar facilmente
        startForeground(NOTIFICATION_ID, createOngoingNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListeningForAlerts()
        return START_STICKY // Tenta reiniciar se for morto pelo sistema
    }

    private fun startListeningForAlerts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            stopSelf()
            return
        }

        // Remove listener anterior se existir
        alertListener?.remove()

        // Ouve apenas alertas ACTIVOS dirigidos a ESTE monitor
        alertListener = db.collection("alerts")
            .whereEqualTo("monitorId", currentUser.uid)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Listen falhou: $e")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    // Verifica MUDANÇAS no documento (apenas novos alertas)
                    for (dc in snapshots.documentChanges) {
                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                // Importante: Verifica se o alerta é recente (ex: últimos 5 min)
                                // para evitar notificar alertas velhos ao abrir a app
                                val timestamp = dc.document.getTimestamp("timestamp")
                                if (timestamp != null) {
                                    val diff = System.currentTimeMillis() - timestamp.toDate().time
                                    // Se o alerta tem menos de 5 minutos, notifica
                                    if (diff < 5 * 60 * 1000) {
                                        val protectedName = dc.document.getString("protectedName") ?: "Unknown"
                                        val type = dc.document.getString("type") ?: "ALERTA"
                                        sendSystemNotification(protectedName, type)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
    }

    private fun sendSystemNotification(name: String, type: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Pode adicionar extras para abrir diretamente no ecrã de alertas
            putExtra("NAVIGATE_TO", "monitorDashboard")
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val typePt = when(type) {
            "FALL" -> getString(R.string.alert_type_fall)
            "ACCIDENT" -> getString(R.string.alert_type_accident)
            "PANIC_BUTTON" -> getString(R.string.alert_type_panic)
            "INACTIVITY" -> getString(R.string.alert_type_inactivity)
            "SPEED" -> getString(R.string.alert_type_speed)
            "GEOFENCING" -> getString(R.string.alert_type_geofencing)
            else -> getString(R.string.alert_type_generic)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_safetysec)
            .setContentTitle(getString(R.string.default_alert_title) + ": $name")
            .setContentText(typePt)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createOngoingNotification(): Notification {
        // Notificação persistente a dizer que o serviço está a correr
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_service_notification_title))
            .setContentText(getString(R.string.monitor_service_notification_text))
            .setSmallIcon(R.drawable.ic_safetysec)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.monitor_channel_desc)
            enableVibration(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        alertListener?.remove()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}