@file:OptIn(ExperimentalTime::class)

package de.binarynoise.captiveportalautologin

import java.io.FileNotFoundException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.filedb.JsonDB
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.HttpStatusCodeException
import okhttp3.HttpUrl.Companion.toHttpUrl

const val API_BASE = "https://captiveportalautologin.binarynoise.de/api/"

private val localCacheRoot = applicationContext.cacheDir.toPath().resolve("Stats")
private val jsonDB = JsonDB(localCacheRoot)

// Worker class to handle the upload
class StatsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    
    var lastRetryTime by SharedPreferences.stats_last_retry_time
    private val retryCooldown = 5.minutes
    
    private fun hasRecentRetry(): Boolean {
        return (Clock.System.now() - Instant.fromEpochMilliseconds(lastRetryTime)) < retryCooldown
    }
    
    private fun recordRetry() {
        lastRetryTime = Clock.System.now().toEpochMilliseconds()
    }
    
    override suspend fun doWork(): Result {
        log("StatsWorker started")
        
        if (hasRecentRetry()) {
            log("Recent retry detected, delaying this attempt to avoid spamming")
            delay(retryCooldown)
        }
        
        val type = inputData.getString("type") ?: return Result.failure()
        val key = inputData.getString("key") ?: return Result.failure()
        
        val apiBaseFromPreference by SharedPreferences.api_base
        val apiClient = ApiClient((apiBaseFromPreference.takeUnless { it == "" } ?: API_BASE).toHttpUrl())
        
        try {
            when (type) {
                "har" -> {
                    val har = jsonDB.load<HAR>(key, "har")
                    apiClient.har.submitHar(key, har)
                    jsonDB.delete<HAR>(key, "har")
                    log("Uploaded HAR $key")
                }
                "error" -> {
                    val error = jsonDB.load<Api.Liberator.Error>(key)
                    apiClient.liberator.reportError(error)
                    jsonDB.delete<Api.Liberator.Error>(key)
                    log("Uploaded Api.Liberator.Error $key")
                }
                "success" -> {
                    val success = jsonDB.load<Api.Liberator.Success>(key)
                    apiClient.liberator.reportSuccess(success)
                    jsonDB.delete<Api.Liberator.Success>(key)
                    log("Uploaded Api.Liberator.Success $key")
                }
            }
            
            return Result.success()
        } catch (e: FileNotFoundException) {
            log("Failed to upload $type $key", e)
            return Result.failure()
        } catch (e: HttpStatusCodeException) {
            when (e.code) {
                429 -> {
                    val timeout = e.response.header("Retry-After")?.toLongOrNull() ?: 0
                    log("Failed to upload $type $key: HTTP 429 - timeout: $timeout")
                    recordRetry()
                    delay(timeout.seconds)
                    return Result.retry()
                }
                
                500, 501, 502, 503, 504, 507 -> {
                    log("Server error trying to upload $type $key: HTTP ${e.code}, trying again later", e)
                    recordRetry()
                    return Result.retry()
                }
                
                else -> {
                    log("Failed to upload $type $key: HTTP ${e.code}", e)
                    return Result.failure()
                }
            }
        } catch (e: Exception) {
            log("Failed to upload $type $key", e)
            recordRetry()
            return Result.retry()
        }
    }
}

object Stats : Api {
    override val har: Har = Har()
    override val liberator: Liberator = Liberator()
    
    
    class Har : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            val key = name
            jsonDB.store(key, har, "har")
            scheduleUpload("har", key)
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
            scheduleUpload("error", key)
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            val key = "${System.currentTimeMillis()}_${success.hashCode()}"
            jsonDB.store(key, success)
            scheduleUpload("success", key)
        }
    }
    
    private fun scheduleUpload(type: String, key: String) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
        val inputData = androidx.work.workDataOf("type" to type, "key" to key)
        val uploadRequest =
            OneTimeWorkRequestBuilder<StatsWorker>().setConstraints(constraints).setInputData(inputData).build()
        
        WorkManager.getInstance(applicationContext).enqueue(uploadRequest)
        log("Scheduled upload for $type: $key")
    }
}
