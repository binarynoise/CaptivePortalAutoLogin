package de.binarynoise.captiveportalautologin.client

import java.security.PublicKey
import kotlin.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.util.json.prettyPrinter
import de.binarynoise.util.json.serializer
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.putJson
import de.binarynoise.util.okhttp.readText
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class ApiClient(private val base: HttpUrl, private val signature: PublicKey?) : Api {
    private val httpClient = OkHttpClient.Builder().addInterceptor(::addSignatureInterceptor).build()
    
    fun addSignatureInterceptor(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (signature == null) return chain.proceed(originalRequest)
        val signatureAuth = signature.encoded.toHexString()
        val newRequest = originalRequest.newBuilder().header("Authorization", "Signature $signatureAuth").build()
        return chain.proceed(newRequest)
    }
    
    override val har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            put("har/$name", har.toJsonElement())
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
        minimum: Int?,
    ): List<String> {
        return serializer.decodeFromString(
            httpClient.get(
                base,
                "ssid",
                queryParameters = mapOf(
                    "limit" to limit,
                    "majorVersion" to majorVersion,
                    "since" to since,
                    "minimum" to minimum,
                ).filterNot { it.value == null }.mapValues { it.value.toString() },
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

fun HAR.toJsonElement(): JsonElement = serializer.encodeToJsonElement(this)
fun HAR.toJson(): String = prettyPrinter.encodeToString(this)
