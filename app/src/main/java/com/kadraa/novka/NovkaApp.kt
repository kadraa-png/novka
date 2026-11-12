package com.kadraa.novka

import android.app.Application
import com.kadraa.novka.capture.CaptureProcessor
import com.kadraa.novka.capture.Notifier
import com.kadraa.novka.data.CurrencyApiProvider
import com.kadraa.novka.data.ErApiProvider
import com.kadraa.novka.data.NovkaDb
import com.kadraa.novka.data.Repository
import com.kadraa.novka.data.SettingsStore
import com.kadraa.novka.widget.BalanceWidget
import com.kadraa.novka.work.Workers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NovkaApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set
    lateinit var capture: CaptureProcessor
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(NovkaDb.create(this), SettingsStore(this), listOf(ErApiProvider(), CurrencyApiProvider()))
        capture = CaptureProcessor(repo) { getString(R.string.cash) }
        repo.onChanged = {
            BalanceWidget.refresh(this)
            val main = repo.settings.current().mainCurrency
            repo.newBudgetAlerts().forEach { Notifier.showBudgetAlert(this, it, main) }
        }
        Notifier.createChannels(this)
        Workers.schedule(this)
        scope.launch {
            if (repo.settings.current().onboarded) {
                repo.runRecurring()
                BalanceWidget.refresh(this@NovkaApp)
            }
        }
    }
}
