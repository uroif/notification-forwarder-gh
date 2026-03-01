package com.uroif.notificationforwarder
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
class SupabaseFragment : Fragment() {
    private lateinit var enableSupabaseSwitch: SwitchMaterial
    private lateinit var supabaseUrlEditText: EditText
    private lateinit var supabaseAnonKeyEditText: EditText
    private lateinit var deviceIdEditText: EditText
    private lateinit var supabaseStatusTextView: TextView
    private lateinit var saveSupabaseButton: Button
    private lateinit var testSupabaseButton: Button
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_supabase, container, false)
        enableSupabaseSwitch = view.findViewById(R.id.enableSupabaseSwitch)
        supabaseUrlEditText = view.findViewById(R.id.supabaseUrlEditText)
        supabaseAnonKeyEditText = view.findViewById(R.id.supabaseAnonKeyEditText)
        deviceIdEditText = view.findViewById(R.id.deviceIdEditText)
        supabaseStatusTextView = view.findViewById(R.id.supabaseStatusTextView)
        saveSupabaseButton = view.findViewById(R.id.saveSupabaseButton)
        testSupabaseButton = view.findViewById(R.id.testSupabaseButton)
        loadConfig()
        enableSupabaseSwitch.setOnCheckedChangeListener { _, _ ->
            updateStatus()
        }
        saveSupabaseButton.setOnClickListener {
            saveConfig()
            Toast.makeText(context, "Supabase configuration saved", Toast.LENGTH_SHORT).show()
        }
        testSupabaseButton.setOnClickListener {
            testConnection()
        }
        return view
    }
    private fun loadConfig() {
        val sharedPref = activity?.getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        ) ?: return
        enableSupabaseSwitch.isChecked = sharedPref.getBoolean("SUPABASE_ENABLED", true)
        supabaseUrlEditText.setText(sharedPref.getString("SUPABASE_URL", ""))
        supabaseAnonKeyEditText.setText(sharedPref.getString("SUPABASE_ANON_KEY", ""))
        deviceIdEditText.setText(sharedPref.getString("DEVICE_ID", ""))
        updateStatus()
    }
    private fun saveConfig() {
        val sharedPref = activity?.getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        ) ?: return
        with(sharedPref.edit()) {
            putBoolean("SUPABASE_ENABLED", enableSupabaseSwitch.isChecked)
            putString("SUPABASE_URL", supabaseUrlEditText.text.toString().trim())
            putString("SUPABASE_ANON_KEY", supabaseAnonKeyEditText.text.toString().trim())
            putString("DEVICE_ID", deviceIdEditText.text.toString().trim())
            apply()
        }
        updateStatus()
    }
    private fun updateStatus() {
        val isEnabled = enableSupabaseSwitch.isChecked
        val hasUrl = supabaseUrlEditText.text.toString().isNotBlank()
        val hasKey = supabaseAnonKeyEditText.text.toString().isNotBlank()
        when {
            !isEnabled -> {
                supabaseStatusTextView.text = "Status: Disabled"
                supabaseStatusTextView.setTextColor(
                    requireContext().getColor(android.R.color.darker_gray)
                )
            }
            !hasUrl || !hasKey -> {
                supabaseStatusTextView.text = "Status: Not configured"
                supabaseStatusTextView.setTextColor(
                    requireContext().getColor(android.R.color.holo_orange_dark)
                )
            }
            else -> {
                supabaseStatusTextView.text = "Status: Configured ✓"
                supabaseStatusTextView.setTextColor(
                    requireContext().getColor(android.R.color.holo_green_dark)
                )
            }
        }
    }
    private fun testConnection() {
        val url = supabaseUrlEditText.text.toString().trim()
        val key = supabaseAnonKeyEditText.text.toString().trim()
        val deviceId = deviceIdEditText.text.toString().trim()
        if (url.isBlank() || key.isBlank()) {
            Toast.makeText(context, "Please fill in URL and Anon Key", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d("SupabaseFragment", "=== TEST CONNECTION STARTED ===")
        Log.d("SupabaseFragment", "URL: $url")
        Log.d("SupabaseFragment", "Device ID: $deviceId")
        lifecycleScope.launch(Dispatchers.IO) {
            var testsPassed = 0
            var testsFailed = 0
            val errors = mutableListOf<String>()
            try {
                Log.d("SupabaseFragment", "[Test 1/3] Testing Supabase connection...")
                val testUrl = java.net.URL("$url/rest/v1/")
                val conn = testUrl.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", key)
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.connectTimeout = 5000
                val responseCode = conn.responseCode
                conn.disconnect()
                if (responseCode in 200..299 || responseCode == 404) {
                    Log.d("SupabaseFragment", "✓ [Test 1/3] Connection successful (HTTP $responseCode)")
                    testsPassed++
                } else {
                    Log.e("SupabaseFragment", "✗ [Test 1/3] Connection failed (HTTP $responseCode)")
                    errors.add("Supabase connection: HTTP $responseCode")
                    testsFailed++
                }
                Log.d("SupabaseFragment", "[Test 2/3] Inserting sample notification to Supabase...")
                try {
                    val supabaseClient = createSupabaseClient(
                        supabaseUrl = url,
                        supabaseKey = key
                    ) {
                        install(Postgrest)
                    }
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    val timestamp = sdf.format(Date())
                    val batteryManager = context?.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    val batteryInfo = if (batteryLevel >= 0) "$batteryLevel%" else "N/A"
                    val sampleData = mapOf(
                        "title" to "Test Connection",
                        "body" to "This is a test notification from Notification Forwarder app - ${Date()}\n\n🔋 Pin: $batteryInfo",
                        "package_name" to "com.uroif.notificationforwarder",
                        "app_name" to "Notification Forwarder",
                        "device_id" to deviceId.ifBlank { null },
                        "timestamp" to timestamp
                    )
                    Log.d("SupabaseFragment", "Sample data: $sampleData")
                    supabaseClient.from("notifications").insert(sampleData)
                    Log.d("SupabaseFragment", "✓ [Test 2/3] Sample notification inserted successfully")
                    testsPassed++
                } catch (e: Exception) {
                    Log.e("SupabaseFragment", "✗ [Test 2/3] Failed to insert sample notification", e)
                    errors.add("Supabase insert: ${e.message}")
                    testsFailed++
                }
                Log.d("SupabaseFragment", "[Test 3/3] Checking Telegram/CMS configuration...")
                val sharedPref = activity?.getSharedPreferences(
                    "com.uroif.notificationforwarder_preferences",
                    Context.MODE_PRIVATE
                )
                val telegramEnabled = sharedPref?.getBoolean("TELEGRAM_ENABLED", true) ?: true
                val botToken = sharedPref?.getString("BOT_TOKEN", "") ?: ""
                val chatIds = sharedPref?.getStringSet("CHAT_IDS", emptySet()) ?: emptySet()
                if (telegramEnabled && botToken.isNotBlank() && chatIds.isNotEmpty()) {
                    Log.d("SupabaseFragment", "Telegram is enabled, sending test message...")
                    val batteryManager = activity?.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    val batteryInfo = if (batteryLevel >= 0) "$batteryLevel%" else "N/A"
                    val message = "<b>Test Connection - Supabase</b>\n" +
                            "✓ Supabase connection successful\n" +
                            "✓ Sample notification inserted\n" +
                            "Device ID: ${deviceId.ifBlank { "Not set" }}\n" +
                            "Time: ${Date()}\n\n" +
                            "🔋 Pin: $batteryInfo"
                    var telegramSuccess = false
                    chatIds.forEach { chatId ->
                        try {
                            Log.d("SupabaseFragment", "Sending to Telegram chat: $chatId")
                            sendMessageToTelegram(botToken, chatId, message)
                            Log.d("SupabaseFragment", "✓ Message sent to Telegram chat: $chatId")
                            telegramSuccess = true
                        } catch (e: Exception) {
                            Log.e("SupabaseFragment", "✗ Failed to send to Telegram chat: $chatId", e)
                            errors.add("Telegram to $chatId: ${e.message}")
                        }
                    }
                    if (telegramSuccess) {
                        Log.d("SupabaseFragment", "✓ [Test 3/3] Telegram message sent successfully")
                        testsPassed++
                    } else {
                        Log.e("SupabaseFragment", "✗ [Test 3/3] All Telegram sends failed")
                        testsFailed++
                    }
                } else {
                    Log.d("SupabaseFragment", "[Test 3/3] Telegram not configured or disabled - skipping")
                    Log.d("SupabaseFragment", "  Enabled: $telegramEnabled, Has Token: ${botToken.isNotBlank()}, Has Chat IDs: ${chatIds.isNotEmpty()}")
                }
                Log.d("SupabaseFragment", "=== TEST CONNECTION COMPLETED ===")
                Log.d("SupabaseFragment", "Tests passed: $testsPassed, Tests failed: $testsFailed")
                withContext(Dispatchers.Main) {
                    val resultMessage = if (testsFailed == 0) {
                        "✓ All tests passed successfully! ($testsPassed/${testsPassed + testsFailed})"
                    } else {
                        "⚠ Tests completed: $testsPassed passed, $testsFailed failed\n${errors.joinToString("\n")}"
                    }
                    Toast.makeText(
                        context,
                        resultMessage,
                        if (testsFailed == 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("SupabaseFragment", "=== TEST CONNECTION FAILED ===", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Connection error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun sendMessageToTelegram(token: String, chatId: String, message: String) {
        val urlString = "https://api.telegram.org/bot$token/sendMessage"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; utf-8")
        conn.doOutput = true
        val jsonInputString = "{\"chat_id\": \"$chatId\", \"text\": \"$message\", \"parse_mode\": \"HTML\"}"
        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonInputString) }
        val responseCode = conn.responseCode
        Log.d("SupabaseFragment", "Telegram API response code for $chatId: $responseCode")
        if (responseCode !in 200..299) {
            val errorStream = conn.errorStream
            val errorMessage = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            throw Exception("HTTP $responseCode: $errorMessage")
        }
    }
}
