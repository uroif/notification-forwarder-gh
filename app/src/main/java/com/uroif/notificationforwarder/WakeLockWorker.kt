package com.uroif.notificationforwarder
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
class WakeLockWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "WakeLockWorker"
        private const val WORK_NAME = "WakeLockPrimary"
        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED) 
                    .setRequiresBatteryNotLow(false) 
                    .setRequiresCharging(false) 
                    .setRequiresDeviceIdle(false) 
                    .build()
                val workRequest = PeriodicWorkRequestBuilder<WakeLockWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .addTag("wake_lock_primary")
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP, 
                        workRequest
                    )
                Log.d(TAG, "✓ WorkManager scheduled (PRIMARY mechanism, 15 min interval)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to schedule WorkManager", e)
            }
        }
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Log.d(TAG, "✓ WorkManager cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to cancel WorkManager", e)
            }
        }
    }
    override suspend fun doWork(): Result {
        Log.d(TAG, "⏰ WorkManager triggered - PRIMARY wake lock verification")
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val tempWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotificationForwarder::WorkerWakeLock"
        )
        try {
            tempWakeLock.acquire(60_000) 
            val sharedPref = applicationContext.getSharedPreferences(
                "com.uroif.notificationforwarder_preferences",
                Context.MODE_PRIVATE
            )
            val serviceEnabled = sharedPref.getBoolean("SERVICE_ENABLED", false)
            if (!serviceEnabled) {
                Log.d(TAG, "Service not enabled, skipping verification")
                return Result.success()
            }
            val intent = Intent(applicationContext, NotificationService::class.java).apply {
                action = NotificationService.ACTION_VERIFY_WAKE_LOCK
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
                Log.d(TAG, "✓ Triggered NotificationService verification")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start service", e)
                return Result.retry()
            }
            updateVerificationStats(sharedPref)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Worker execution failed", e)
            return Result.retry()
        } finally {
            try {
                if (tempWakeLock.isHeld) {
                    tempWakeLock.release()
                    Log.d(TAG, "✓ Temporary wake lock released")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing temp wake lock", e)
            }
        }
    }
    private fun updateVerificationStats(sharedPref: android.content.SharedPreferences) {
        try {
            val currentCount = sharedPref.getInt("WORKMANAGER_VERIFY_COUNT", 0)
            val lastVerifyTime = System.currentTimeMillis()
            with(sharedPref.edit()) {
                putInt("WORKMANAGER_VERIFY_COUNT", currentCount + 1)
                putLong("WORKMANAGER_LAST_VERIFY", lastVerifyTime)
                apply()
            }
            Log.d(TAG, "📊 WorkManager verification count: ${currentCount + 1}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update stats", e)
        }
    }
}
