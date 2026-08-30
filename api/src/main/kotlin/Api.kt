package de.binarynoise.captiveportalautologin.api

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import de.binarynoise.captiveportalautologin.api.json.har.HAR

interface Api {
    val har: Har
    val log: Log
    val liberator: Liberator
    
    interface Har {
        fun submitHar(name: String, har: HAR)
    }
    
    interface Log {
        fun submitLog(name: String, log: String)
    }
    
    interface Liberator {
        fun getLiberatorVersion(): String
        fun fetchLiberatorUpdate()
        
        @Serializable
        data class Error(
            val version: String,
            val timestamp: Long,
            val ssid: String,
            val url: String?,
            val message: String?,
            val solver: String?,
            val stackTrace: String?,
            val har: HAR?,
        )
        
        @Serializable
        data class Success(
            val version: String,
            val timestamp: Long,
            val ssid: String,
            val url: String,
            val solver: String,
        )
        
        fun reportError(error: Error)
        fun reportSuccess(success: Success)
    }
    
    suspend fun getSSIDs(
        limit: Int? = null,
        maximumMajorVersion: Int? = null,
        since: Instant? = null,
        minimumSuccesses: Int? = null,
        minimumBayesianRating: Float? = null,
        bayesianWeight: Int? = null,
    ): List<String>
    
    @Serializable
    data class Update(
        val version: String,
        val url: String,
    )
    
    suspend fun checkUpdate(
        installedVersion: String,
        manual: Boolean,
    ): Update?
}
