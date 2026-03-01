package com.uroif.notificationforwarder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed - checking if services should auto-start")
            try {
                val sharedPref = context.getSharedPreferences(
                    "com.uroif.notificationforwarder_preferences",
                    Context.MODE_PRIVATE
                )
                val serviceEnabled = sharedPref.getBoolean("SERVICE_ENABLED", false)
                if (serviceEnabled) {
                    Log.d(TAG, "SERVICE_ENABLED is true, starting NotificationService")
                    val serviceIntent = Intent(context, NotificationService::class.java)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Log.d(TAG, "✓ NotificationService started after boot")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to start NotificationService after boot", e)
                    }
                } else {
                    Log.d(TAG, "SERVICE_ENABLED is false, skipping NotificationService auto-start")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error auto-starting services after boot", e)
            }
        }
    }
    companion object {
        private const val TAG = "BootReceiver"
    }
}
