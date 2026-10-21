package dev.creatorpulse.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.creatorpulse.App
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = App.get(applicationContext)
        return try {
            app.repo.refreshAll(WidgetData.widgetUniverseIds(applicationContext))
            WidgetData.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            app.settings.saveLastError(e.message ?: e.javaClass.simpleName)
            WidgetData.updateAllWidgets(applicationContext)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}

object RefreshScheduler {
    private const val PERIODIC = "creatorpulse.refresh.periodic"
    private const val ONCE = "creatorpulse.refresh.once"

    fun schedule(context: Context, minutes: Int, replace: Boolean) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun refreshNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, req)
    }
}
