package de.binarynoise.captiveportalautologin.json.har

import java.net.HttpCookie
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    
    fun fillInPostData(body: RequestBody?) {
        if (body == null) return
        with(body) {
            if (formData != null) {
                postData = PostData(
                    "multipart/form-data",
                    formData.map { (key, values) ->
                        values.map { value ->
                            PostParam(key, value, null, null)
                        }
                    }.flatten(),
                    formData.flatMap { (key, values) ->
                        values.map { value ->
                            "$key=$value"
                        }
                    }.joinToString("\n"),
                )
                log("Filled in POST data (${postData?.params?.size} params)")
            } else if (raw != null) {
                when (raw.size) {
                    0 -> {
                        log("POST data is empty")
                    }
                    1 -> {
                        postData = PostData(
                            "text/plain",
                            null,
                            raw[0].bytes?.toByteArray()?.decodeToString(),
                        )
                        log("Filled in POST data (${postData?.text?.length} bytes)")
                    }
                    else -> {
                        postData = PostData(
                            "text/plain",
                            null,
                            raw.map { it.bytes?.toByteArray()?.decodeToString() }.joinToString("\n\n"),
                        )
                        log("Filled in POST data (${postData?.text?.length} bytes)")
                    }
                }
            }
        }
    }
    
    private fun fillInHeaders(requestHeaders: Array<HttpHeader>) {
        headers += requestHeaders.map { Header(it) }
        log("Filled in headers (${headers.size} headers)")
    }
    
    private fun fillInCookies(requestHeaders: Array<HttpHeader>) {
        cookies += requestHeaders.asSequence()
            .filter { it.name.startsWith("SetCookie", ignoreCase = true) }
            .flatMap { HttpCookie.parse(it.value) }
            .map(::Cookie)
        log("Filled in cookies (${cookies.size} cookies)")
    }
    
    fun handleRequestHeaders(requestHeaders: Array<HttpHeader>?) {
        if (requestHeaders == null) {
            return
        }
        
        fillInHeaders(requestHeaders)
        fillInCookies(requestHeaders)
    }
}
