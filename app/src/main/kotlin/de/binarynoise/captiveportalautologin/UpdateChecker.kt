package de.binarynoise.captiveportalautologin

import java.util.concurrent.TimeUnit
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.englishResources
import de.binarynoise.captiveportalautologin.util.getSignaturePublicKey
import de.binarynoise.logger.Logger.log

class UpdateCheckerWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParams,
) {
    override suspend fun doWork(): Result = try {
        val apiBaseUrl = SharedPreferences.api_base_url.get()
        if (apiBaseUrl == null) {
            log(applicationContext.englishResources.getString(R.string.error_api_base_url_not_set))
            return Result.failure()
        }
        val apiClient = ApiClient(apiBaseUrl, applicationContext.getSignaturePublicKey())
        
        log("Checking for updates")
        val update = apiClient.checkUpdate(BuildConfig.VERSION_NAME, false)
        if (update == null) {
            log("No update available")
            return Result.success()
        }
        log("Update available: ${update.version}")
        
        var lastNotifiedUpdateVersion by SharedPreferences.last_notified_update_version
        if (lastNotifiedUpdateVersion == update.version) return Result.success()
        
        val intent = Intent(Intent.ACTION_VIEW, update.url.toUri())
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        
        val notificationChannel = NotificationChannel(
            "update",
            applicationContext.getString(R.string.update),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        
        val notification = NotificationCompat.Builder(applicationContext, "update")
            .setContentTitle("Update available")
            .setContentText("Update to version ${update.version}")
            .setSmallIcon(R.drawable.wifi_lock_open)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = applicationContext.getSystemService<NotificationManager>() ?: return Result.failure()
        notificationManager.createNotificationChannel(notificationChannel)
        notificationManager.notify("update".hashCode(), notification)
        
        lastNotifiedUpdateVersion = update.version
        Result.success()
    } catch (e: Exception) {
        log("Error checking for updates", e)
        Result.failure()
    }
}

private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

fun enqueueUpdateCheckWork(
    context: Context = applicationContext,
    singleShot: Boolean = false,
) {
    val workManager = WorkManager.getInstance(context)
    if (singleShot) {
        log("enqueue expedited update check work")
        val workRequest = OneTimeWorkRequest.Builder(UpdateCheckerWorker::class.java).apply {
            setConstraints(constraints)
            addTag(UpdateCheckerWorker::class.java.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
        }.build()
        workManager.enqueue(workRequest)
    } else {
        val workRequest = PeriodicWorkRequest.Builder(UpdateCheckerWorker::class.java, 7, TimeUnit.DAYS).apply {
            setConstraints(constraints)
            addTag(UpdateCheckerWorker::class.java.name)
        }.build()
        workManager.enqueueUniquePeriodicWork(
            UpdateCheckerWorker::class.java.name,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }
}
