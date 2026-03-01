package com.uroif.notificationforwarder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
class NotificationService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var sharedPreferences: SharedPreferences? = null
    private var supabaseClient: io.github.jan.supabase.SupabaseClient? = null
    private var ttsManager: TTSManager? = null
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "SELECTED_APPS") {
            Log.d(TAG, "SELECTED_APPS changed, updating notification")
            updateForegroundNotification()
        }
    }
    companion object {
        private const val TAG = "NotificationService"
        const val ACTION_VERIFY_WAKE_LOCK = "com.uroif.notificationforwarder.VERIFY_WAKE_LOCK"
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val sharedPref = getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE)
        val serviceEnabled = sharedPref.getBoolean("SERVICE_ENABLED", false)
        if (!serviceEnabled) {
            Log.d(TAG, "Service is disabled. Ignoring notification.")
            return
        }
        val selectedApps = sharedPref.getStringSet("SELECTED_APPS", emptySet()) ?: emptySet()
        val packageName = sbn.packageName
        Log.d(TAG, "Notification received from: $packageName")
        if (!selectedApps.contains(packageName)) {
            return
        }
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = bigText ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) {
            return
        }
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
        serviceScope.launch {
            val batteryLevel = getBatteryLevel()
            val batteryInfo = if (batteryLevel >= 0) "$batteryLevel%" else "N/A"
            Log.d(TAG, "Battery level retrieved: $batteryLevel")
            val telegramEnabled = sharedPref.getBoolean("TELEGRAM_ENABLED", false)
            val supabaseEnabled = sharedPref.getBoolean("SUPABASE_ENABLED", true)
            if (telegramEnabled) {
                val botToken = sharedPref.getString("BOT_TOKEN", "")
                val chatIds = sharedPref.getStringSet("CHAT_IDS", emptySet()) ?: emptySet()
                if (!botToken.isNullOrBlank() && chatIds.isNotEmpty()) {
                    val message = "<b>$title</b>\n$text\n\n🔋 Pin: $batteryInfo"
                    Log.d(TAG, "Forwarding to Telegram: $message")
                    chatIds.forEach { chatId ->
                        sendMessageToTelegram(botToken, chatId, message)
                    }
                }
            }
            if (supabaseEnabled) {
                val bodyWithBattery = "$text\n\n🔋 Pin: $batteryInfo"
                sendToSupabase(packageName, appName, title, bodyWithBattery)
            }
            saveToHistory(title, text)
            processTTS(text, appName)
        }
    }
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate - Starting optimized foreground service")
        SharedNotificationManager.createNotificationChannel(this)
        SharedNotificationManager.updateServiceStatus(notificationService = true)
        startForegroundService()
        acquireWakeLock()
        acquireWifiLock()
        WakeLockWorker.schedule(this)
        registerPreferenceListener()
        initSupabaseClient()
        initTTSManager()
        requestBatteryOptimizationExemption()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_VERIFY_WAKE_LOCK -> {
                verifyAndReacquireWakeLock()
            }
        }
        return START_STICKY
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        SharedNotificationManager.createNotificationChannel(this)
        SharedNotificationManager.updateServiceStatus(notificationService = true)
        startForegroundService()
        acquireWakeLock()
        acquireWifiLock()
        registerPreferenceListener()
        initSupabaseClient()
    }
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        releaseWakeLock()
        releaseWifiLock()
        unregisterPreferenceListener()
    }
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy - Cleaning up resources")
        SharedNotificationManager.updateServiceStatus(notificationService = false)
        WakeLockWorker.cancel(this)
        releaseWakeLock()
        releaseWifiLock()
        unregisterPreferenceListener()
        supabaseClient = null
        ttsManager?.shutdown()
        ttsManager = null
        super.onDestroy()
    }
    private fun initSupabaseClient() {
        val sharedPref = getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        )
        val url = sharedPref.getString("SUPABASE_URL", "") ?: ""
        val key = sharedPref.getString("SUPABASE_ANON_KEY", "") ?: ""
        if (url.isNotBlank() && key.isNotBlank()) {
            try {
                supabaseClient = createSupabaseClient(
                    supabaseUrl = url,
                    supabaseKey = key
                ) {
                    install(Postgrest)
                }
                Log.d(TAG, "Supabase client initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Supabase client", e)
            }
        }
    }
    private fun startForegroundService() {
        updateSharedNotificationStats()
        val notification = SharedNotificationManager.buildNotification(this)
        startForeground(SharedNotificationManager.NOTIFICATION_ID, notification)
        Log.d(TAG, "Started as foreground service")
    }
    private fun updateForegroundNotification() {
        try {
            updateSharedNotificationStats()
            SharedNotificationManager.updateNotification(this)
            Log.d(TAG, "Foreground notification updated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification", e)
        }
    }
    private fun updateSharedNotificationStats() {
        val sharedPref = getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        )
        val selectedApps = sharedPref.getStringSet("SELECTED_APPS", emptySet())
        val appCount = selectedApps?.size ?: 0
        SharedNotificationManager.updateLockStatus(
            wakeLock = wakeLock?.isHeld ?: false,
            wifiLock = wifiLock?.isHeld ?: false
        )
        SharedNotificationManager.updateStats(
            appsCount = appCount,
            lostCount = 0,
            failures = 0
        )
    }
    private fun registerPreferenceListener() {
        try {
            sharedPreferences = getSharedPreferences(
                "com.uroif.notificationforwarder_preferences",
                Context.MODE_PRIVATE
            )
            sharedPreferences?.registerOnSharedPreferenceChangeListener(prefsListener)
            Log.d(TAG, "SharedPreferences listener registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register preference listener", e)
        }
    }
    private fun unregisterPreferenceListener() {
        try {
            sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefsListener)
            sharedPreferences = null
            Log.d(TAG, "SharedPreferences listener unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister preference listener", e)
        }
    }
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                Log.d(TAG, "Wake lock already held")
                return
            }
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NotificationForwarder::KeepAwake"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "✓ Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to acquire wake lock", e)
        }
    }
    private fun verifyAndReacquireWakeLock() {
        val wakeLockHeld = wakeLock?.isHeld ?: false
        val wifiLockHeld = wifiLock?.isHeld ?: false
        Log.d(TAG, "🔍 Verification: wake=$wakeLockHeld, wifi=$wifiLockHeld")
        if (!wakeLockHeld) {
            Log.w(TAG, "⚠️ Wake lock lost! Re-acquiring...")
            acquireWakeLock()
        }
        if (!wifiLockHeld) {
            Log.w(TAG, "⚠️ WiFi lock lost! Re-acquiring...")
            acquireWifiLock()
        }
        updateForegroundNotification()
    }
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "✓ Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing wake lock", e)
        }
    }
    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                Log.d(TAG, "WiFi lock already held")
                return
            }
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "NotificationForwarder::WifiLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "✓ WiFi lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to acquire WiFi lock", e)
        }
    }
    private fun releaseWifiLock() {
        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "✓ WiFi lock released")
                }
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing WiFi lock", e)
        }
    }
    @Suppress("DEPRECATION")
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "✓ App already whitelisted from battery optimization")
                return
            }
            Log.w(TAG, "⚠️ App NOT whitelisted - Battery optimization may affect delivery!")
            val sharedPref = getSharedPreferences(
                "com.uroif.notificationforwarder_preferences",
                Context.MODE_PRIVATE
            )
            val alreadyAsked = sharedPref.getBoolean("BATTERY_WHITELIST_ASKED", false)
            if (!alreadyAsked) {
                Log.d(TAG, "📋 Requesting battery optimization exemption...")
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    with(sharedPref.edit()) {
                        putBoolean("BATTERY_WHITELIST_ASKED", true)
                        apply()
                    }
                    Log.d(TAG, "✓ Battery optimization dialog opened")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Cannot open battery optimization settings", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery optimization", e)
        }
    }
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery level", e)
            -1
        }
    }
    private fun sendMessageToTelegram(token: String, chatId: String, message: String) {
        try {
            val urlString = "https://api.telegram.org/bot$token/sendMessage"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.doOutput = true
            val jsonInputString = "{\"chat_id\": \"$chatId\", \"text\": \"$message\", \"parse_mode\": \"HTML\"}"
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonInputString) }
            Log.d(TAG, "Telegram API response code for $chatId: ${conn.responseCode}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to $chatId", e)
        }
    }
    private suspend fun sendToSupabase(
        packageName: String,
        appName: String,
        title: String,
        body: String
    ) {
        try {
            if (supabaseClient == null) {
                Log.w(TAG, "Supabase client not initialized")
                return
            }
            val sharedPref = getSharedPreferences(
                "com.uroif.notificationforwarder_preferences",
                Context.MODE_PRIVATE
            )
            val deviceId = sharedPref.getString("DEVICE_ID", "") ?: ""
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val timestamp = sdf.format(Date())
            supabaseClient?.from("notifications")?.insert(
                mapOf(
                    "title" to title,
                    "body" to body,
                    "package_name" to packageName,
                    "app_name" to appName,
                    "device_id" to deviceId.ifBlank { null },
                    "timestamp" to timestamp
                )
            )
            Log.d(TAG, "✓ Sent to Supabase: $title")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to send to Supabase", e)
        }
    }
    private fun saveToHistory(title: String, body: String) {
        val sharedPref = applicationContext.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPref.getString("HISTORY_LOGS", "[]")
        val type = object : TypeToken<MutableList<HistoryLog>>() {}.type
        val logs: MutableList<HistoryLog> = gson.fromJson(json, type)
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
        val recentLogs = logs.filter { it.timestamp >= sevenDaysAgo }.toMutableList()
        recentLogs.add(HistoryLog(title, body, System.currentTimeMillis()))
        val newJson = gson.toJson(recentLogs)
        with(sharedPref.edit()) {
            putString("HISTORY_LOGS", newJson)
            apply()
        }
    }
    private fun initTTSManager() {
        try {
            ttsManager = TTSManager.getInstance(applicationContext)
            Log.d(TAG, "TTS Manager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS Manager", e)
        }
    }
    private fun processTTS(notificationText: String, appName: String) {
        try {
            val sharedPref = getSharedPreferences(
                "com.uroif.notificationforwarder_preferences",
                Context.MODE_PRIVATE
            )
            val speakEnabled = sharedPref.getBoolean("SPEAK_ENABLED", true)
            if (!speakEnabled) {
                return
            }
            val extractRegex = sharedPref.getString("SPEAK_EXTRACT_REGEX", "\\+[\\d.,]+\\s*VND") ?: "\\+[\\d.,]+\\s*VND"
            val cleanRegex = sharedPref.getString("SPEAK_CLEAN_REGEX", "[^\\d]") ?: "[^\\d]"
            val extractPattern = Regex(extractRegex)
            val matchResult = extractPattern.find(notificationText)
            if (matchResult == null) {
                Log.d(TAG, "No amount found in notification")
                return
            }
            val rawAmount = matchResult.value
            Log.d(TAG, "Extracted amount: $rawAmount")
            val cleanPattern = Regex(cleanRegex)
            val cleanedAmount = rawAmount.replace(cleanPattern, "")
            if (cleanedAmount.isBlank()) {
                Log.w(TAG, "Cleaned amount is empty")
                return
            }
            val includeAppName = sharedPref.getBoolean("SPEAK_INCLUDE_APP_NAME", false)
            val template = if (includeAppName) {
                sharedPref.getString("SPEAK_TEMPLATE_WITH_APP", "{app_name}, {debit_amount} đồng")
            } else {
                sharedPref.getString("SPEAK_TEMPLATE", "{debit_amount} đồng")
            } ?: "{debit_amount} đồng"
            var speechText = template.replace("{debit_amount}", cleanedAmount)
            if (includeAppName) {
                speechText = speechText.replace("{app_name}", appName)
            }
            val locale = sharedPref.getString("SPEAK_VOICE_LOCALE", "vi-VN") ?: "vi-VN"
            val speed = sharedPref.getFloat("SPEAK_SPEED", 1.0f)
            val pitch = sharedPref.getFloat("SPEAK_PITCH", 1.0f)
            kotlinx.coroutines.MainScope().launch {
                ttsManager?.speak(speechText, speed, pitch, locale)
                Log.d(TAG, "✓ TTS: $speechText")
            }
            saveToSpeakHistory(appName, cleanedAmount, speechText)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing TTS", e)
        }
    }
    private fun saveToSpeakHistory(appName: String, amount: String, fullText: String) {
        try {
            val sharedPref = applicationContext.getSharedPreferences(
                "com.uroif.notificationforwarder_preferences",
                Context.MODE_PRIVATE
            )
            val gson = Gson()
            val json = sharedPref.getString("SPEAK_HISTORY_LOGS", "[]")
            val type = object : TypeToken<MutableList<SpeakHistoryLog>>() {}.type
            val logs: MutableList<SpeakHistoryLog> = gson.fromJson(json, type) ?: mutableListOf()
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
            val recentLogs = logs.filter { it.timestamp >= sevenDaysAgo }.toMutableList()
            recentLogs.add(SpeakHistoryLog(System.currentTimeMillis(), appName, amount, fullText))
            val newJson = gson.toJson(recentLogs)
            with(sharedPref.edit()) {
                putString("SPEAK_HISTORY_LOGS", newJson)
                apply()
            }
            Log.d(TAG, "Saved to speak history: $appName - $amount")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to speak history", e)
        }
    }
}
