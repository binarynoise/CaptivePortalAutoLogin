package de.binarynoise.captiveportalautologin.client

import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.putJson
import de.binarynoise.util.okhttp.readText
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class ApiClient(private val base: HttpUrl) : Api {
    private val httpClient = OkHttpClient()
    
    override val har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            put("har/$name", har.toJson())
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
            put("liberator/error", serializer.encodeToJsonElement(error))
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            put("liberator/success", serializer.encodeToJsonElement(success))
        }
    }
    
    override suspend fun getSSIDs(
        limit: Int?,
        majorVersion: Int?,
        since: Instant?,
    ): List<String> {
        return serializer.decodeFromString(
            httpClient.get(
                base,
                "api/ssid",
                queryParameters = mapOf(
                    "limit" to limit,
                    "majorVersion" to majorVersion,
                    "since" to since,
                ).filterNot { it.value == null }.mapValues { it.toString() },
            ).readText()
        )
    }
    
    private fun post(url: String, json: JsonElement) {
        httpClient.postJson(base, url, json).use { it.checkSuccess() }
    }
    
    private fun put(url: String, json: JsonElement) {
        httpClient.putJson(base, url, json).use { it.checkSuccess() }
    }
}

fun HAR.toJson(): JsonElement = serializer.encodeToJsonElement(this)

val serializer = Json {
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = false
}
