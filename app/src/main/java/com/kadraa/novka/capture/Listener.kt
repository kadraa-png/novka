package com.kadraa.novka.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kadraa.novka.NovkaApp
import com.kadraa.novka.domain.Source
import kotlinx.coroutines.launch

class NovkaNotificationListener : NotificationListenerService() {
    private val defaultSmsApp by lazy { runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        // Anything from an app that is not a known source is dropped here and never stored.
        val identified = Sources.identify(sbn.packageName, title, defaultSmsApp) ?: return
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (text.isBlank()) return

        val app = application as NovkaApp
        val postedAt = sbn.postTime
        app.scope.launch {
            val settings = app.repo.settings.current()
            val enabled = when (identified.source) {
                Source.WALLET -> settings.walletEnabled
                Source.RAIFFEISEN -> settings.raiffeisenEnabled
                else -> false
            }
            if (!settings.onboarded || !enabled) return@launch
            runCatching { app.capture.process(identified.source, identified.strict, title, text, postedAt) }
                .getOrNull()
                ?.let { Notifier.showPrompt(app, it, settings.hideAmountOnLockScreen) }
        }
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            val me = ComponentName(context, NovkaNotificationListener::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
