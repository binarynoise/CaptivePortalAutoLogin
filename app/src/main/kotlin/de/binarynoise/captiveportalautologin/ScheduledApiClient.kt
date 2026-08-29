package de.binarynoise.captiveportalautologin

import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.binarynoise.captiveportalautologin.BuildConfig.API_BASE
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.getSignaturePublicKey
import de.binarynoise.filedb.JsonDB
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.HttpStatusCodeException
import de.binarynoise.util.okhttp.parseRetryAfterOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val localCacheRoot = applicationContext.cacheDir.toPath().resolve("Stats")
private val jsonDB = JsonDB(localCacheRoot)


object ScheduledApiClient : Api {
    override val har: Har = Har()
    override val log: Log = Log()
    override val liberator: Liberator = Liberator()
    
    class Har : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            jsonDB.store(name, har, "har")
            enqueueStatsUploadWork()
        }
    }
    
    class Log : Api.Log {
        override fun submitLog(name: String, log: String) {
            jsonDB.store(name, log, "log")
            enqueueStatsUploadWork()
        }
    }
    
    class Liberator : Api.Liberator {
        override fun getLiberatorVersion(): String {
            TODO("Not yet implemented")
        }
        
        override fun fetchLiberatorUpdate() {
            TODO("Not yet implemented")
        }
        
        override fun reportError(error: Api.Liberator.Error) {
            val key = "${System.currentTimeMillis()}_${error.hashCode()}"
            jsonDB.store(key, error)
            enqueueStatsUploadWork()
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            val key = "${System.currentTimeMillis()}_${success.hashCode()}"
            jsonDB.store(key, success)
            enqueueStatsUploadWork()
        }
    }
    
    @Deprecated("use ApiClient directly", level = DeprecationLevel.HIDDEN)
    override suspend fun getSSIDs(
        limit: Int?,
        maximumMajorVersion: Int?,
        since: Instant?,
        minimum: Int?,
        minimumBayesianRating: Float?,
        bayesianWeight: Int?,
    ): List<String> {
        throw UnsupportedOperationException("use ApiClient directly")
    }
}

class HarStatsWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : StatsWorker<HAR>(
    appContext,
    workerParams,
    type = "HAR",
    keys = { jsonDB.listAll<HAR>("har") },
    load = { jsonDB.load<HAR>(it, "har") },
    upload = { key, har, apiClient -> apiClient.har.submitHar(key, har) },
    delete = { jsonDB.delete<HAR>(it, "har") },
)

class SuccessStatsWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : StatsWorker<Api.Liberator.Success>(
    appContext,
    workerParams,
    type = "Success",
    keys = { jsonDB.listAll<Api.Liberator.Success>() },
    load = { jsonDB.load<Api.Liberator.Success>(it) },
    upload = { key, success, apiClient -> apiClient.liberator.reportSuccess(success) },
    delete = { jsonDB.delete<Api.Liberator.Success>(it) },
)

class ErrorStatsWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : StatsWorker<Api.Liberator.Error>(
    appContext,
    workerParams,
    type = "Error",
    keys = { jsonDB.listAll<Api.Liberator.Error>() },
    load = { jsonDB.load<Api.Liberator.Error>(it) },
    upload = { key, error, apiClient -> apiClient.liberator.reportError(error) },
    delete = { jsonDB.delete<Api.Liberator.Error>(it) },
)

class LogStatsWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : StatsWorker<String>(
    appContext,
    workerParams,
    type = "Log",
    keys = { jsonDB.listAll<String>("log") },
    load = { jsonDB.load<String>(it, "log") },
    upload = { key, log, apiClient -> apiClient.log.submitLog(key, log) },
    delete = { jsonDB.delete<String>(it, "log") },
)

abstract class StatsWorker<T : Any>(
    appContext: Context,
    workerParams: WorkerParameters,
    val type: String,
    val keys: () -> Collection<String>,
    val load: (key: String) -> T,
    val upload: (key: String, T, ApiClient) -> Unit,
    val delete: (key: String) -> Unit,
) : CoroutineWorker(appContext, workerParams) {
    
    private val retryStatusCodes = arrayOf(429, 500, 502, 503, 504, 506, 507)
    
    override suspend fun doWork(): Result {
        var savedRetryAfter by SharedPreferences.stats_retry_after(type)
        val now = Clock.System.now()
        if (savedRetryAfter > now && !inputData.getBoolean("skipDelay", false)) {
            log("Skipping $type upload - retry after ${savedRetryAfter - now}")
            return Result.retry()
        }
        
        val apiBaseFromPreference by SharedPreferences.api_base
        val apiBaseUrl =
            (apiBaseFromPreference.takeUnless { it == "" } ?: API_BASE).toHttpUrlOrNull() ?: return Result.failure()
        val apiClient = ApiClient(apiBaseUrl, applicationContext.getSignaturePublicKey())
        
        var shouldRetry = false
        
        
        for (key in keys()) {
            log("Uploading $type $key")
            try {
                val item = load(key)
                upload(key, item, apiClient)
                delete(key)
                log("Uploaded $type $key")
            } catch (e: HttpStatusCodeException) {
                log("Failed to upload $type $key: HTTP ${e.code}")
                if (e.code in retryStatusCodes) {
                    shouldRetry = true
                    val now = Clock.System.now()
                    savedRetryAfter = now + (parseRetryAfterOrNull(e.response.headers) ?: 5.minutes)
                    break
                } else {
                    delete(key)
                }
            } catch (e: Exception) {
                log("Failed to upload $type $key", e)
                shouldRetry = true
                val now = Clock.System.now()
                savedRetryAfter = now + 5.minutes
                break
            }
        }
        
        return if (shouldRetry) Result.retry() else Result.success()
    }
}

private val statsWorkerClasses = listOf(
    HarStatsWorker::class.java,
    SuccessStatsWorker::class.java,
    ErrorStatsWorker::class.java,
    LogStatsWorker::class.java,
)

private fun getStatsUploadUniqueWorkName(cls: Class<out StatsWorker<*>>): String = "StatsUpload ${cls.simpleName}"

private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

fun enqueueStatsUploadWork(
    context: Context = applicationContext,
    singleShot: Boolean = false,
) {
    val workManager = WorkManager.getInstance(context)
    if (singleShot) {
        log("enqueue expedited stats upload work")
        val inputData = workDataOf("skipDelay" to true)
        statsWorkerClasses.forEach {
            val workRequest = OneTimeWorkRequest.Builder(it).apply {
                setConstraints(constraints)
                addTag(StatsWorker::class.java.name)
                setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
                setInputData(inputData)
            }.build()
            workManager.enqueue(workRequest)
        }
    } else {
        log("enqueuing periodic stats upload work")
        
        statsWorkerClasses.forEach {
            val workRequest = PeriodicWorkRequest.Builder(it, 1, TimeUnit.HOURS).apply {
                setConstraints(constraints)
                addTag(StatsWorker::class.java.name)
            }.build()
            workManager.enqueueUniquePeriodicWork(
                getStatsUploadUniqueWorkName(it),
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
        }
    }
}

fun dequeueStatsUploadWork(context: Context = applicationContext) {
    val workManager = WorkManager.getInstance(context)
    statsWorkerClasses.forEach {
        workManager.cancelUniqueWork(getStatsUploadUniqueWorkName(it))
    }
}

fun getEnqueuedStatsUploadWork(context: Context = applicationContext): Flow<List<WorkInfo>> {
    return WorkManager.getInstance(context).getWorkInfosByTagFlow(StatsWorker::class.java.name)
}
