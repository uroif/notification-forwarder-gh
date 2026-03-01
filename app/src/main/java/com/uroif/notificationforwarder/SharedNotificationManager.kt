package com.uroif.notificationforwarder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
object SharedNotificationManager {
    private const val TAG = "SharedNotificationMgr"
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "notification_forwarder_service"
    private var notificationServiceRunning = false
    private var batteryMonitorServiceRunning = false
    private var wakeLockHeld = false
    private var wifiLockHeld = false
    private var monitoredAppsCount = 0
    private var wakeLockLostCount = 0
    private var consecutiveFailures = 0
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notification Forwarder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors notifications and keeps device awake"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }
    fun updateServiceStatus(
        notificationService: Boolean? = null,
        batteryMonitorService: Boolean? = null,
        aggressiveWakeService: Boolean? = null  
    ) {
        notificationService?.let { notificationServiceRunning = it }
        batteryMonitorService?.let { batteryMonitorServiceRunning = it }
    }
    fun updateLockStatus(
        wakeLock: Boolean? = null,
        wifiLock: Boolean? = null
    ) {
        wakeLock?.let { wakeLockHeld = it }
        wifiLock?.let { wifiLockHeld = it }
    }
    fun updateStats(
        appsCount: Int? = null,
        lostCount: Int? = null,
        failures: Int? = null
    ) {
        appsCount?.let { monitoredAppsCount = it }
        lostCount?.let { wakeLockLostCount = it }
        failures?.let { consecutiveFailures = it }
    }
    fun buildNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val batteryLevel = getBatteryLevel(context)
        val batteryText = if (batteryLevel >= 0) "🔋 $batteryLevel%" else ""
        val inDoze = isDeviceInDozeMode(context)
        val deviceStatus = if (inDoze) "💤 Doze" else "Active"
        val hasWarning = consecutiveFailures >= 3 || !wakeLockHeld || !wifiLockHeld
        val title = if (hasWarning) {
            "⚠️ Notification Forwarder"
        } else {
            "📡 Notification Forwarder"
        }
        val shortContent = buildString {
            append("Monitoring $monitoredAppsCount apps")
            if (batteryLevel >= 0) {
                append(" • $batteryText")
            }
        }
        val expandedContent = buildString {
            append("📱 Apps monitored: $monitoredAppsCount\n")
            if (batteryLevel >= 0) {
                append("🔋 Battery: $batteryLevel%\n")
            }
            append("⚡ Wake Lock: ${if (wakeLockHeld) "Active ✓" else "LOST ✗"}\n")
            append("📶 WiFi Lock: ${if (wifiLockHeld) "Active ✓" else "LOST ✗"}\n")
            append("🔔 Battery Monitor: ${if (batteryMonitorServiceRunning) "Active ✓" else "Inactive"}\n")
            append(" Device: $deviceStatus\n")
            if (hasWarning) {
                append("\n⚠️ ")
                if (consecutiveFailures > 0) {
                    append("$consecutiveFailures lock failures • ")
                }
                if (!wakeLockHeld || !wifiLockHeld) {
                    append("Locks lost • ")
                }
                append("Check settings")
            } else {
                append("\n✓ All systems operational")
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    fun updateNotification(context: Context) {
        try {
            val notification = buildNotification(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "✓ Notification updated")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update notification", e)
        }
    }
    private fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
    }
    private fun isDeviceInDozeMode(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isDeviceIdleMode
        } else {
            false
        }
    }
}
