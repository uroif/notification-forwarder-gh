package com.uroif.notificationforwarder
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
class TTSManager private constructor(context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    companion object {
        @Volatile
        private var instance: TTSManager? = null
        fun getInstance(context: Context): TTSManager {
            return instance ?: synchronized(this) {
                instance ?: TTSManager(context.applicationContext).also { instance = it }
            }
        }
    }
    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.forLanguageTag("vi-VN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("TTSManager", "Vietnamese language not fully supported, using default")
                    textToSpeech?.setLanguage(Locale.getDefault())
                }
                isInitialized = true
                Log.d("TTSManager", "TextToSpeech initialized successfully")
            } else {
                Log.e("TTSManager", "TextToSpeech initialization failed")
            }
        }
    }
    fun speak(text: String, speed: Float = 1.0f, pitch: Float = 1.0f, locale: String = "vi-VN") {
        if (!isInitialized) {
            Log.w("TTSManager", "TTS not initialized yet")
            return
        }
        try {
            val ttsLocale = when (locale) {
                "vi-VN" -> Locale.forLanguageTag("vi-VN")
                "en-US" -> Locale.US
                else -> Locale.getDefault()
            }
            val result = textToSpeech?.setLanguage(ttsLocale)
            when (result) {
                TextToSpeech.LANG_MISSING_DATA -> {
                    Log.e("TTSManager", "Language data is missing for ${ttsLocale.displayName}")
                    Log.e("TTSManager", "Please install Vietnamese TTS data from Google Play Store")
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.e("TTSManager", "Language ${ttsLocale.displayName} is not supported")
                    Log.e("TTSManager", "Please install Vietnamese TTS data from Google Play Store")
                }
                TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                    Log.d("TTSManager", "Language ${ttsLocale.displayName} set successfully")
                }
            }
            textToSpeech?.setSpeechRate(speed)
            textToSpeech?.setPitch(pitch)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
            Log.d("TTSManager", "Speaking: $text (locale: $locale, speed: $speed, pitch: $pitch)")
        } catch (e: Exception) {
            Log.e("TTSManager", "Error speaking text", e)
        }
    }
    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e("TTSManager", "Error stopping TTS", e)
        }
    }
    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            isInitialized = false
            Log.d("TTSManager", "TTS shutdown")
        } catch (e: Exception) {
            Log.e("TTSManager", "Error shutting down TTS", e)
        }
    }
    fun getAvailableVoices(): List<String> {
        val voices = mutableListOf<String>()
        try {
            textToSpeech?.voices?.forEach { voice ->
                voices.add("${voice.locale.displayName} (${voice.locale})")
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "Error getting available voices", e)
        }
        return voices.ifEmpty { listOf("Vietnamese (vi-VN)", "English (en-US)") }
    }
    fun isLanguageAvailable(locale: String): Boolean {
        val ttsLocale = when (locale) {
            "vi-VN" -> Locale.forLanguageTag("vi-VN")
            "en-US" -> Locale.US
            else -> Locale.getDefault()
        }
        val result = textToSpeech?.isLanguageAvailable(ttsLocale)
        return result == TextToSpeech.LANG_AVAILABLE || 
               result == TextToSpeech.LANG_COUNTRY_AVAILABLE || 
               result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }
}
