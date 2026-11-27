package com.example

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.RemoteNotificationEntry

class RemoteNotificationListenerService : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: RemoteNotificationListenerService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = try {
                Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            } catch (_: Exception) {
                null
            } ?: return instance != null
            val me = ComponentName(context, RemoteNotificationListenerService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val syncRunnable = Runnable { pushSnapshot() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        scheduleSync(delayMs = 150L)
    }

    override fun onListenerDisconnected() {
        main.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        scheduleSync(delayMs = 350L)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        scheduleSync(delayMs = 350L)
    }

    private fun scheduleSync(delayMs: Long) {
        main.removeCallbacks(syncRunnable)
        main.postDelayed(syncRunnable, delayMs)
    }

    fun pushSnapshot() {
        val list = collectActiveNotifications()
        RemoteService.instance?.onNotificationsSnapshot(listenerEnabled = true, list = list)
    }

    fun collectActiveNotifications(): List<RemoteNotificationEntry> {
        val active = try {
            activeNotifications
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val pm = packageManager
        val out = ArrayList<RemoteNotificationEntry>(active.size)
        for (sbn in active) {
            val notif = sbn.notification ?: continue
            val pkg = sbn.packageName?.take(120) ?: continue
            val extras = notif.extras ?: continue
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val bodyText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
            val message = bigText.ifBlank { bodyText }.ifBlank { subText }
            if (title.isBlank() && message.isBlank()) continue

            val appLabel = try {
                val ai = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                pm.getApplicationLabel(ai).toString().trim().ifBlank { pkg }
            } catch (_: Throwable) {
                try {
                    @Suppress("DEPRECATION")
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString().trim().ifBlank { pkg }
                } catch (_: Exception) {
                    pkg
                }
            }

            out.add(
                RemoteNotificationEntry(
                    key = sbn.key.orEmpty().take(160),
                    packageName = pkg,
                    appLabel = appLabel.take(80),
                    title = title.take(200),
                    text = message.take(500),
                    postedAtMs = sbn.postTime,
                    ongoing = sbn.isOngoing,
                ),
            )
        }
        return out.sortedByDescending { it.postedAtMs }.take(80)
    }
}
