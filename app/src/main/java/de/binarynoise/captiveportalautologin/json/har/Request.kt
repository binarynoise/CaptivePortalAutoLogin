package de.binarynoise.captiveportalautologin.json.har

import java.net.HttpCookie
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.util.Base64
import de.binarynoise.captiveportalautologin.json.webRequest.HttpHeader
import de.binarynoise.captiveportalautologin.json.webRequest.OnBeforeRequestDetails
import de.binarynoise.captiveportalautologin.json.webRequest.RequestBody
import de.binarynoise.logger.Logger.log

@Serializable
class Request(
    /**
     * Request method (GET, POST, ...).
     */
    @SerialName("method") var method: String,
    /**
     * Absolute URL of the request (fragments are not included).
     */
    @SerialName("url") var url: String,
    /**
     * Request HTTP Version.
     */
    @SerialName("httpVersion") var httpVersion: String,
    /**
     * List of cookie objects.
     */
    @SerialName("cookies") var cookies: MutableSet<Cookie>,
    /**
     * List of header objects.
     */
    @SerialName("headers") var headers: MutableSet<Header>,
    /**
     * List of query parameter objects.
     */
    @SerialName("queryString") var query: MutableList<Query>,
    /**
     * Posted data info.
     */
    @SerialName("postData") var postData: PostData?,
    /**
     * Total number of bytes from the start of the HTTP request message until (and including) the double CRLF before the body.
     * Set to 0 if the info is not available.
     */
    @SerialName("headersSize") var headersSize: Int,
    /**
     * Size of the request body (POST data payload) in bytes.
     * Set to 0 if the info is not available.
     */
    @SerialName("bodySize") var bodySize: Int,
) {
    val totalSize get() = headersSize + bodySize
    
    constructor(onBeforeRequestDetails: OnBeforeRequestDetails) : this(
        onBeforeRequestDetails.method,
        onBeforeRequestDetails.url,
        "",
        cookies = mutableSetOf(),
        headers = mutableSetOf(),
        query = mutableListOf(),
        postData = null,
        0,
        0,
    ) {
        fillInPostData(onBeforeRequestDetails.requestBody)
    }
    
    init {
        log("> $method $url")
    }
    
    fun handleRequestHeaders(requestHeaders: Array<HttpHeader>?) {
        if (requestHeaders == null) {
            return
        }
        
        fillInHeaders(requestHeaders)
        fillInCookies(requestHeaders)
    }
    
    private fun fillInHeaders(requestHeaders: Array<HttpHeader>) {
        val modified = headers.addAll(requestHeaders.map { Header(it) })
        if (modified) requestHeaders.forEach { log("> ${it.name}: ${it.value}") }
    }
    
    private fun fillInCookies(requestHeaders: Array<HttpHeader>) {
        val newCookies = requestHeaders.asSequence()
            .filter { it.name.contains("Cookie", ignoreCase = true) }
            .flatMap { HttpCookie.parse(it.value) }
            .map(::Cookie)
            .toList()
        val modified = cookies.addAll(newCookies)
        if (modified) log("Filled in cookies: ${newCookies.size} new, ${cookies.size} total")
    }
    
    private fun fillInPostData(body: RequestBody?) = with(body) {
        if (this == null) return
        
        if (formData != null) {
            postData = PostData(
                "multipart/form-data",
                formData.flatMap { (key, values) ->
                    values.map { value ->
                        PostParam(key, value, null, null)
                    }
                },
                formData.flatMap { (key, values) ->
                    values.map { value ->
                        "$key=$value"
                    }
                }.joinToString("\n"),
            )
            log("Filled in POST data (${postData?.params?.size} params)")
            return
        }
        
        if (raw == null) {
            log("POST data is null")
            return
        }
        if (raw.size == 0) {
            log("POST data is empty")
            return
        }
        
        if (raw.size == 1) {
            val contentString = raw[0].bytes?.toByteArray()?.decodeToString()
            if (contentString != null && !contentString.looksLikeBinaryData(0.2)) {
                postData = PostData(
                    "text/plain",
                    null,
                    contentString,
                )
                log("Filled in POST data (${contentString.length} bytes)")
                return
            }
        }
        
        val params: List<PostParam> = raw.mapIndexed { i, data ->
            val bytes: ByteArray = data.bytes?.toByteArray() ?: ByteArray(0)
            val contentString = bytes.decodeToString()
            if (contentString.looksLikeBinaryData(0.2)) {
                val encodedContent = Base64.encodeToString(contentString.toByteArray(), Base64.DEFAULT)
                PostParam(i.toString(), encodedContent, data.file, "application/octet-stream")
            } else {
                PostParam(i.toString(), contentString, data.file, "text/plain")
            }
        }.filterNotNull()
        
        postData = PostData(
            "multipart/form-data",
            params,
            params.joinToString("\n\n"),
        )
        log("Filled in POST data (${params.size} params)")
    }
}
