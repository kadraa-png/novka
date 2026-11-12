package com.kadraa.novka.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kadraa.novka.NovkaApp
import java.util.concurrent.TimeUnit

class RatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as NovkaApp).repo
        return if (repo.refreshRates()) Result.success() else Result.retry()
    }
}

class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as NovkaApp).repo
        if (repo.settings.current().onboarded) repo.runRecurring()
        return Result.success()
    }
}

object Workers {
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork(
            "rates", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RatesWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
        manager.enqueueUniquePeriodicWork(
            "recurring", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
