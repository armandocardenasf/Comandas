package com.enverolab.comandas.corporate

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.enverolab.comandas.data.PosDatabase

class CorporateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val db = PosDatabase(applicationContext)
        return try {
            val m = CorporateManager(applicationContext, db)
            if (m.enabled()) {
                m.sendPending()
                try {
                    m.syncCatalog()
                } catch (_: Exception) {
                    /* Se reintentará en un punto seguro. */
                }
            }
            Result.success()
        } catch (e: CorporateHttpError) {
            if (e.status in listOf(400, 401, 403, 409)) Result.failure() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }

    companion object {
        fun schedule(c: Context) {
            val w =
                OneTimeWorkRequestBuilder<CorporateWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(c)
                .enqueueUniqueWork("corporate-send", ExistingWorkPolicy.KEEP, w)
        }

        fun periodic(c: Context) {
            val w =
                PeriodicWorkRequestBuilder<CorporateWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build()
            WorkManager.getInstance(c)
                .enqueueUniquePeriodicWork("corporate-periodic", ExistingPeriodicWorkPolicy.KEEP, w)
        }

        fun cancel(c: Context) {
            WorkManager.getInstance(c).cancelUniqueWork("corporate-periodic")
            WorkManager.getInstance(c).cancelUniqueWork("corporate-send")
        }
    }
}
