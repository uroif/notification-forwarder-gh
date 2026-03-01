package com.uroif.notificationforwarder
import android.graphics.drawable.Drawable
data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isBatteryOptimized: Boolean = false 
)
