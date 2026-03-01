package com.uroif.notificationforwarder
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
class TelegramFragment : Fragment() {
    private lateinit var enableTelegramSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var botTokenEditText: EditText
    private lateinit var chatIdContainer: LinearLayout
    private lateinit var addChatIdButton: Button
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_telegram, container, false)
        enableTelegramSwitch = view.findViewById(R.id.enableTelegramSwitch)
        botTokenEditText = view.findViewById(R.id.botTokenEditText)
        chatIdContainer = view.findViewById(R.id.chatIdContainer)
        addChatIdButton = view.findViewById(R.id.addChatIdButton)
        saveButton = view.findViewById(R.id.saveButton)
        testButton = view.findViewById(R.id.testButton)
        loadConfig()
        addChatIdButton.setOnClickListener {
            if (chatIdContainer.childCount < 5) {
                addChatIdField()
            } else {
                Toast.makeText(context, "You can add a maximum of 5 Chat IDs.", Toast.LENGTH_SHORT).show()
            }
        }
        saveButton.setOnClickListener {
            saveConfig()
            Toast.makeText(context, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
        }
        testButton.setOnClickListener {
            sendTestMessage()
        }
        return view
    }
    private fun addChatIdField(chatId: String = "") {
        val inflater = LayoutInflater.from(context)
        val chatFieldView = inflater.inflate(R.layout.list_item_chat_id, chatIdContainer, false)
        val editText = chatFieldView.findViewById<EditText>(R.id.chatIdEditText)
        editText.setText(chatId)
        val removeButton = chatFieldView.findViewById<View>(R.id.removeChatIdButton)
        removeButton.setOnClickListener {
            chatIdContainer.removeView(chatFieldView)
        }
        chatIdContainer.addView(chatFieldView)
    }
    private fun getChatIds(): Set<String> {
        val chatIds = mutableSetOf<String>()
        for (i in 0 until chatIdContainer.childCount) {
            val view = chatIdContainer.getChildAt(i)
            val editText = view.findViewById<EditText>(R.id.chatIdEditText)
            val id = editText.text.toString().trim()
            if (id.isNotEmpty()) {
                chatIds.add(id)
            }
        }
        return chatIds
    }
    private fun saveConfig() {
        val sharedPref = activity?.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putBoolean("TELEGRAM_ENABLED", enableTelegramSwitch.isChecked)
            putString("BOT_TOKEN", botTokenEditText.text.toString())
            putStringSet("CHAT_IDS", getChatIds())
            apply()
        }
    }
    private fun loadConfig() {
        val sharedPref = activity?.getSharedPreferences("com.uroif.notificationforwarder_preferences", Context.MODE_PRIVATE) ?: return
        enableTelegramSwitch.isChecked = sharedPref.getBoolean("TELEGRAM_ENABLED", false)
        botTokenEditText.setText(sharedPref.getString("BOT_TOKEN", ""))
        val chatIds = sharedPref.getStringSet("CHAT_IDS", emptySet())
        chatIdContainer.removeAllViews()
        if (chatIds.isNullOrEmpty()) {
            addChatIdField() 
        } else {
            chatIds.forEach { addChatIdField(it) }
        }
    }
    private fun sendTestMessage() {
        val token = botTokenEditText.text.toString().trim()
        val chatIds = getChatIds()
        if (token.isBlank() || chatIds.isEmpty()) {
            Toast.makeText(context, getString(R.string.fill_in_all_fields), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            var successCount = 0
            chatIds.forEach { chatId ->
                try {
                    val message = "This is a test message from Notification Forwarder."
                    val urlString = "https://api.telegram.org/bot$token/sendMessage"
                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.doOutput = true
                    val jsonInputString = "{\"chat_id\": \"$chatId\", \"text\": \"$message\"}"
                    OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonInputString) }
                    if (conn.responseCode == 200) {
                        successCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Test message sent to $successCount/${chatIds.size} chats.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
