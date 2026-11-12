package com.kadraa.novka.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kadraa.novka.NovkaApp
import com.kadraa.novka.R
import com.kadraa.novka.data.BudgetAlert
import com.kadraa.novka.domain.Money
import com.kadraa.novka.ui.MainActivity
import kotlinx.coroutines.launch

object Notifier {
    private const val REVIEW = "review"
    private const val BUDGETS = "budgets"
    const val ACTION_SET_CATEGORY = "com.kadraa.novka.SET_CATEGORY"
    const val EXTRA_TX = "tx"
    const val EXTRA_CATEGORY = "category"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(REVIEW, context.getString(R.string.channel_review), NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(BUDGETS, context.getString(R.string.channel_budgets), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun promptId(txId: Long) = 10_000 + (txId % 1_000_000).toInt()

    private fun post(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Permission was withdrawn between the check and the call; the payment is still under "Needs review".
        }
    }

    fun showPrompt(context: Context, prompt: Prompt, hideAmount: Boolean) {
        val amount = Money.format(prompt.amount, prompt.currency)
        val merchant = prompt.merchant.ifBlank { context.getString(R.string.payment) }
        val guess = prompt.guesses.firstOrNull()?.second
        val open = PendingIntent.getActivity(
            context, promptId(prompt.txId),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_REVIEW_TX, prompt.txId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun build(showAmount: Boolean): NotificationCompat.Builder = NotificationCompat.Builder(context, REVIEW)
            .setSmallIcon(R.drawable.ic_stat_novka)
            .setContentTitle(if (showAmount) "$merchant · $amount" else merchant)
            .setContentText(if (guess != null) context.getString(R.string.prompt_guess, guess) else context.getString(R.string.prompt_pick))
            .setContentIntent(open)
            .setAutoCancel(true)

        val builder = build(showAmount = true).setVisibility(if (hideAmount) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
        if (hideAmount) builder.setPublicVersion(build(showAmount = false).build())
        // Android shows at most three actions: the two likeliest categories, then the full picker.
        prompt.guesses.take(2).forEachIndexed { index, (categoryId, label) ->
            val intent = Intent(context, CaptureActionReceiver::class.java).setAction(ACTION_SET_CATEGORY)
                .putExtra(EXTRA_TX, prompt.txId).putExtra(EXTRA_CATEGORY, categoryId)
            val pending = PendingIntent.getBroadcast(
                context, (prompt.txId % 100_000_000).toInt() * 10 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, if (index == 0) "✓ $label" else label, pending)
        }
        builder.addAction(0, context.getString(R.string.other), open)
        post(context, promptId(prompt.txId), builder.build())
    }

    fun cancelPrompt(context: Context, txId: Long) = NotificationManagerCompat.from(context).cancel(promptId(txId))

    fun showBudgetAlert(context: Context, alert: BudgetAlert, mainCurrency: String) {
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val title = context.getString(if (alert.level >= 100) R.string.budget_alert_over else R.string.budget_alert_near, alert.categoryName)
        val text = context.getString(R.string.budget_alert_text, Money.format(alert.spent, mainCurrency), Money.format(alert.limit, mainCurrency))
        post(
            context, 20_000 + (alert.budgetId % 1_000_000).toInt(),
            NotificationCompat.Builder(context, BUDGETS).setSmallIcon(R.drawable.ic_stat_novka).setContentTitle(title).setContentText(text)
                .setContentIntent(open).setAutoCancel(true).build(),
        )
    }
}

/** Handles the category buttons on a payment prompt, including from the lock screen. */
class CaptureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifier.ACTION_SET_CATEGORY) return
        val txId = intent.getLongExtra(Notifier.EXTRA_TX, 0)
        val categoryId = intent.getLongExtra(Notifier.EXTRA_CATEGORY, 0)
        if (txId == 0L || categoryId == 0L) return
        val app = context.applicationContext as NovkaApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repo.setCategory(txId, categoryId)
                Notifier.cancelPrompt(app, txId)
            } finally {
                pending.finish()
            }
        }
    }
}
