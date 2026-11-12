package com.kadraa.novka.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kadraa.novka.NovkaApp
import com.kadraa.novka.R
import com.kadraa.novka.domain.CurrencyConverter
import com.kadraa.novka.domain.Money
import com.kadraa.novka.domain.TxType
import com.kadraa.novka.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

class BalanceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as NovkaApp
        val pending = goAsync()
        app.scope.launch {
            try {
                refresh(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidget::class.java))
            if (ids.isEmpty()) return
            val repo = (context.applicationContext as NovkaApp).repo
            val settings = repo.settings.current()
            val main = settings.mainCurrency
            val rates = repo.rateMap()

            val total = repo.dao.accountsWithBalance().filter { !it.account.archived }.sumOf { row ->
                CurrencyConverter.rate(row.account.currency, main, rates)?.let { CurrencyConverter.convert(row.balance, row.account.currency, main, it) } ?: 0L
            }
            val zone = ZoneId.systemDefault()
            val month = YearMonth.now()
            val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val spent = -repo.dao.txsBetween(from, to).filter { it.type == TxType.EXPENSE }.sumOf { it.mainAmount }

            // With the app lock on, amounts stay off the home screen.
            val hidden = settings.lockEnabled
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            views.setTextViewText(R.id.widget_balance, if (hidden) "•••••" else Money.format(total, main))
            views.setTextViewText(R.id.widget_spent, context.getString(R.string.widget_spent, if (hidden) "•••" else Money.format(spent, main)))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 30, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            views.setOnClickPendingIntent(
                R.id.widget_add,
                PendingIntent.getActivity(
                    context, 31,
                    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ADD, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(ids, views)
        }
    }
}
