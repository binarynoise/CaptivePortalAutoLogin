package de.binarynoise.captiveportalautologin.client

import kotlinx.serialization.json.Json
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.LOG
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.putJson
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class ApiClient(private val base: HttpUrl) : Api {
    private val httpClient = OkHttpClient()
    
    override val har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            put("har/$name", har.toJson())
        }
    }
    
    override val log = object : Api.Log {
        override fun submitLog(name: String, log: LOG) {
            put("log/$name", log.toJson())
        }
    }
    
    override val liberator = object : Api.Liberator {
        override fun getLiberatorVersion(): String {
            TODO("Not yet implemented")
        }
        
        override fun fetchLiberatorUpdate() {
            TODO("Not yet implemented")
        }
        
        override fun reportError(error: Api.Liberator.Error) {
            put("liberator/error", serializer.encodeToString(error))
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            put("liberator/success", serializer.encodeToString(success))
        }
    }
    
    private fun post(url: String, json: String) {
        httpClient.postJson(base, url, json).use { it.checkSuccess() }
    }
    
    private fun put(url: String, json: String) {
        httpClient.putJson(base, url, json).use { it.checkSuccess() }
    }
}

fun HAR.toJson(): String = serializer.encodeToString(this)
fun LOG.toJson(): String = serializer.encodeToString(this)

val serializer = Json {
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = false
}
