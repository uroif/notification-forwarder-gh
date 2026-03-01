package com.uroif.notificationforwarder
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
class SpeakFragment : Fragment() {
    private lateinit var enableSpeakSwitch: SwitchMaterial
    private lateinit var includeAppNameSwitch: SwitchMaterial
    private lateinit var extractRegexEditText: EditText
    private lateinit var cleanRegexEditText: EditText
    private lateinit var speechTemplateEditText: EditText
    private lateinit var speechTemplateWithAppEditText: EditText
    private lateinit var voiceSpinner: Spinner
    private lateinit var speedSeekBar: SeekBar
    private lateinit var pitchSeekBar: SeekBar
    private lateinit var speedLabel: TextView
    private lateinit var pitchLabel: TextView
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: SpeakHistoryAdapter
    private lateinit var ttsManager: TTSManager
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_speak, container, false)
        enableSpeakSwitch = view.findViewById(R.id.enableSpeakSwitch)
        includeAppNameSwitch = view.findViewById(R.id.includeAppNameSwitch)
        extractRegexEditText = view.findViewById(R.id.extractRegexEditText)
        cleanRegexEditText = view.findViewById(R.id.cleanRegexEditText)
        speechTemplateEditText = view.findViewById(R.id.speechTemplateEditText)
        speechTemplateWithAppEditText = view.findViewById(R.id.speechTemplateWithAppEditText)
        voiceSpinner = view.findViewById(R.id.voiceSpinner)
        speedSeekBar = view.findViewById(R.id.speedSeekBar)
        pitchSeekBar = view.findViewById(R.id.pitchSeekBar)
        speedLabel = view.findViewById(R.id.speedLabel)
        pitchLabel = view.findViewById(R.id.pitchLabel)
        saveButton = view.findViewById(R.id.saveButton)
        testButton = view.findViewById(R.id.testButton)
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView)
        ttsManager = TTSManager.getInstance(requireContext())
        setupVoiceSpinner()
        setupSeekBars()
        setupHistoryRecyclerView()
        loadConfig()
        saveButton.setOnClickListener {
            saveConfig()
            Toast.makeText(context, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
        }
        testButton.setOnClickListener {
            testSpeech()
        }
        return view
    }
    override fun onResume() {
        super.onResume()
        loadHistory()
    }
    private fun setupVoiceSpinner() {
        val voices = listOf("Vietnamese (vi-VN)", "English (en-US)")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, voices)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        voiceSpinner.adapter = adapter
    }
    private fun setupSeekBars() {
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress / 100f) * 1.5f
                speedLabel.text = getString(R.string.speed_label_value, String.format("%.1f", speed))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        pitchSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress / 100f) * 1.5f
                pitchLabel.text = getString(R.string.pitch_label_value, String.format("%.1f", pitch))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    private fun setupHistoryRecyclerView() {
        historyAdapter = SpeakHistoryAdapter(emptyList())
        historyRecyclerView.layoutManager = LinearLayoutManager(context)
        historyRecyclerView.adapter = historyAdapter
    }
    private fun loadConfig() {
        val sharedPref = activity?.getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        ) ?: return
        enableSpeakSwitch.isChecked = sharedPref.getBoolean("SPEAK_ENABLED", true)
        includeAppNameSwitch.isChecked = sharedPref.getBoolean("SPEAK_INCLUDE_APP_NAME", false)
        extractRegexEditText.setText(
            sharedPref.getString("SPEAK_EXTRACT_REGEX", "\\+[\\d.,]+\\s*VND")
        )
        cleanRegexEditText.setText(
            sharedPref.getString("SPEAK_CLEAN_REGEX", "[^\\d]")
        )
        speechTemplateEditText.setText(
            sharedPref.getString("SPEAK_TEMPLATE", "{debit_amount} đồng")
        )
        speechTemplateWithAppEditText.setText(
            sharedPref.getString("SPEAK_TEMPLATE_WITH_APP", "{app_name}, {debit_amount} đồng")
        )
        val locale = sharedPref.getString("SPEAK_VOICE_LOCALE", "vi-VN")
        voiceSpinner.setSelection(if (locale == "en-US") 1 else 0)
        val speed = sharedPref.getFloat("SPEAK_SPEED", 1.0f)
        val speedProgress = ((speed - 0.5f) / 1.5f * 100).toInt()
        speedSeekBar.progress = speedProgress.coerceIn(0, 150)
        val pitch = sharedPref.getFloat("SPEAK_PITCH", 1.0f)
        val pitchProgress = ((pitch - 0.5f) / 1.5f * 100).toInt()
        pitchSeekBar.progress = pitchProgress.coerceIn(0, 150)
    }
    private fun saveConfig() {
        val sharedPref = activity?.getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        ) ?: return
        val locale = when (voiceSpinner.selectedItemPosition) {
            1 -> "en-US"
            else -> "vi-VN"
        }
        val speed = 0.5f + (speedSeekBar.progress / 100f) * 1.5f
        val pitch = 0.5f + (pitchSeekBar.progress / 100f) * 1.5f
        with(sharedPref.edit()) {
            putBoolean("SPEAK_ENABLED", enableSpeakSwitch.isChecked)
            putBoolean("SPEAK_INCLUDE_APP_NAME", includeAppNameSwitch.isChecked)
            putString("SPEAK_EXTRACT_REGEX", extractRegexEditText.text.toString())
            putString("SPEAK_CLEAN_REGEX", cleanRegexEditText.text.toString())
            putString("SPEAK_TEMPLATE", speechTemplateEditText.text.toString())
            putString("SPEAK_TEMPLATE_WITH_APP", speechTemplateWithAppEditText.text.toString())
            putString("SPEAK_VOICE_LOCALE", locale)
            putFloat("SPEAK_SPEED", speed)
            putFloat("SPEAK_PITCH", pitch)
            apply()
        }
    }
    private fun testSpeech() {
        saveConfig()
        val sharedPref = activity?.getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        ) ?: return
        val locale = sharedPref.getString("SPEAK_VOICE_LOCALE", "vi-VN") ?: "vi-VN"
        if (!ttsManager.isLanguageAvailable(locale)) {
            val languageName = if (locale == "vi-VN") "tiếng Việt" else "English"
            Toast.makeText(
                context,
                "⚠️ Không tìm thấy gói ngôn ngữ $languageName!\n\nVui lòng cài đặt:\n1. Mở Settings\n2. Tìm 'Text-to-speech'\n3. Cài đặt gói ngôn ngữ $languageName",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val includeAppName = sharedPref.getBoolean("SPEAK_INCLUDE_APP_NAME", false)
        val template = if (includeAppName) {
            sharedPref.getString("SPEAK_TEMPLATE_WITH_APP", "{app_name}, Bạn đã nhận {debit_amount} đồng")
        } else {
            sharedPref.getString("SPEAK_TEMPLATE", "Bạn đã nhận {debit_amount} đồng")
        } ?: "Bạn đã nhận {debit_amount} đồng"
        val testAmount = "390000"
        val testAppName = "Vietcombank"
        var speech = template.replace("{debit_amount}", testAmount)
        if (includeAppName) {
            speech = speech.replace("{app_name}", testAppName)
        }
        val speed = sharedPref.getFloat("SPEAK_SPEED", 1.0f)
        val pitch = sharedPref.getFloat("SPEAK_PITCH", 1.0f)
        ttsManager.speak(speech, speed, pitch, locale)
        Toast.makeText(context, "🔊 Test: $speech", Toast.LENGTH_LONG).show()
    }
    private fun loadHistory() {
        val sharedPref = activity?.getSharedPreferences(
            "com.uroif.notificationforwarder_preferences",
            Context.MODE_PRIVATE
        ) ?: return
        val gson = Gson()
        val json = sharedPref.getString("SPEAK_HISTORY_LOGS", "[]")
        val type = object : TypeToken<List<SpeakHistoryLog>>() {}.type
        val logs: List<SpeakHistoryLog> = gson.fromJson(json, type) ?: emptyList()
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
        val recentLogs = logs.filter { it.timestamp >= sevenDaysAgo }
            .sortedByDescending { it.timestamp }
        historyAdapter.updateLogs(recentLogs)
    }
    override fun onDestroy() {
        super.onDestroy()
    }
}
