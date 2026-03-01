package com.uroif.notificationforwarder
data class SpeakHistoryLog(
    val timestamp: Long,
    val appName: String,
    val amount: String,
    val fullText: String
)
