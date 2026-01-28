package de.binarynoise.captiveportalautologin.api

import kotlinx.serialization.Serializable
import de.binarynoise.captiveportalautologin.api.json.har.HAR

interface Api {
    val har: Har
    val liberator: Liberator
    
    interface Har {
        fun submitHar(name: String, har: HAR)
    }
    
    interface Liberator {
        fun getLiberatorVersion(): String
        fun fetchLiberatorUpdate()
        
        @Serializable
        data class Error(
            val version: String,
            val timestamp: Long,
            val ssid: String,
            val url: String,
            val message: String,
            // TODO: eventually remove nullable
            val solver: String?,
            val stackTrace: String?,
        )
        
        @Serializable
        data class Success(
            val version: String,
            val timestamp: Long,
            val ssid: String,
            val url: String,
            // TODO: eventually remove nullable
            val solver: String?,
        )
        
        fun reportError(error: Error)
        fun reportSuccess(success: Success)
    }
}
