package pt.isec.amov.safetysec.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import pt.isec.amov.safetysec.R
import java.util.Locale

object PermissionUtils {
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    // Pede permissão (a antiga checkAndRequestOverlay foi dividida para melhor controle da UI)
    fun requestOverlayPermission(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, context.getString(R.string.toast_enable_overlay), Toast.LENGTH_LONG).show()
    }

    // --- 2. BATERIA (ESSENCIAL PARA SAMSUNG) ---
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun requestBatteryExemption(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                val intentGeneral = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intentGeneral.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intentGeneral)
            }
    }

    // --- 3. ESPECÍFICO DA MARCA (XIAOMI/SAMSUNG AVANÇADO) ---

    fun openSpecificPermissions(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
        var intent: Intent? = null

        try {
            when {
                // XIAOMI: Foco em "Outras Permissões" -> Janelas Pop-up
                "xiaomi" in manufacturer -> {
                    intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                    intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    intent.putExtra("extra_pkgname", context.packageName)
                    Toast.makeText(context, context.getString(R.string.toast_xiaomi_instruction), Toast.LENGTH_LONG).show()
                }

                // SAMSUNG: Foco em Bateria (onde o Service costuma morrer)
                "samsung" in manufacturer -> {
                    intent = Intent()
                    intent.component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                    Toast.makeText(context, context.getString(R.string.toast_samsung_instruction), Toast.LENGTH_LONG).show()
                }

                // OPPO
                "oppo" in manufacturer -> {
                    intent = Intent()
                    intent.component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                }

                // HUAWEI
                "huawei" in manufacturer -> {
                    intent = Intent()
                    intent.component = ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity")
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                openAppDetails(context)
            }

        } catch (e: Exception) {
            openAppDetails(context)
        }
    }

    private fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", context.packageName, null)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Toast.makeText(context, context.getString(R.string.toast_check_settings), Toast.LENGTH_LONG).show()
    }
}