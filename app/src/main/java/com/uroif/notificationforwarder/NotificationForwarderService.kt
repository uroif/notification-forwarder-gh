package com.uroif.notificationforwarder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
class NotificationForwarderService : NotificationListenerService() {
    private val TAG = "NotificationService"
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) {
            val packageName = sbn.packageName
            val title = sbn.notification.extras.getString("android.title")
            val text = sbn.notification.extras.getString("android.text")
            Log.i(TAG, "Notification Posted: $packageName - Title: $title, Text: $text")
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.i(TAG, "Notification Removed: ${sbn?.packageName}")
    }
}
