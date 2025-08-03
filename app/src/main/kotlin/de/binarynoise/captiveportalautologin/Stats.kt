package de.binarynoise.captiveportalautologin

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
import okhttp3.HttpUrl.Companion.toHttpUrl

const val API_BASE = "https://am-i-captured.binarynoise.de/api/"

private val localCacheRoot = applicationContext.cacheDir.toPath().resolve("Stats")
private val jsonDB = JsonDB(localCacheRoot)

// Worker class to handle the upload
class StatsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    
    override suspend fun doWork(): Result {
        log("StatsWorker started")
        val apiBaseFromPreference by SharedPreferences.api_base
        val apiClient = ApiClient((apiBaseFromPreference.takeUnless { it == "" } ?: API_BASE).toHttpUrl())
        
        val harFiles = jsonDB.loadAll<HAR>("har")
        harFiles.forEach { (key, har) ->
            try {
                apiClient.har.submitHar(key, har)
                jsonDB.delete<HAR>(key, "har")
                log("Uploaded HAR $key")
            } catch (e: Exception) {
                log("Failed to upload har", e)
                // retry on the next worker run
                return Result.retry()
            }
        }
        
        val errorFiles = jsonDB.loadAll<Api.Liberator.Error>()
        errorFiles.forEach { (key, error) ->
            try {
                apiClient.liberator.reportError(error)
                jsonDB.delete<Api.Liberator.Error>(key)
                log("Uploaded Api.Liberator.Error $key")
            } catch (e: Exception) {
                log("Failed to upload error", e)
                return Result.retry()
            }
        }
        
        val successFiles = jsonDB.loadAll<Api.Liberator.Success>()
        successFiles.forEach { (key, success) ->
            try {
                apiClient.liberator.reportSuccess(success)
                jsonDB.delete<Api.Liberator.Success>(key)
                log("Uploaded Api.Liberator.Success $key")
            } catch (e: Exception) {
                log("Failed to upload success", e)
                return Result.retry()
            }
        }
        
        log("StatsWorker finished")
        return Result.success()
    }
}

object Stats : Api {
    override val har: Har = Har()
    override val liberator: Liberator = Liberator()
    
    
    class Har : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            val key = name
            jsonDB.store(key, har, "har")
            triggerUpload()
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
            triggerUpload()
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            val key = "${System.currentTimeMillis()}_${success.hashCode()}"
            jsonDB.store(key, success)
            triggerUpload()
        }
    }
    
    // Schedule the WorkRequest
    fun triggerUpload() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val uploadRequest = OneTimeWorkRequestBuilder<StatsWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(applicationContext).enqueue(uploadRequest)
        log("Scheduled upload")
    }
}
