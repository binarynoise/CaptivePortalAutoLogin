package de.binarynoise.captiveportalautologin.json.har

import java.net.HttpCookie
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.util.Base64
import de.binarynoise.captiveportalautologin.json.webRequest.HttpHeader
import de.binarynoise.captiveportalautologin.json.webRequest.OnAuthRequiredDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnErrorOccurredDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnHeadersReceivedDetails
import de.binarynoise.logger.Logger.log

@Serializable
class Response(
    /**
     * Response status.
     */
    @SerialName("status") var status: Int,
    /**
     * Response status description.
     */
    @SerialName("statusText") var statusText: String,
    /**
     * Response HTTP Version.
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
     * Details about the response body.
     */
    @SerialName("content") var content: Content,
    /**
     * Redirection target URL from the Location response header.
     */
    @SerialName("redirectURL") var redirectURL: String,
    /**
     * Total number of bytes from the start of the HTTP response message until (and including) the double CRLF before the body. Set to 0 if the info is not available.
     */
    @SerialName("headersSize") var headersSize: Int,
    /**
     * Size of the received response body in bytes. Set to zero in case of responses coming from the cache (304). Set to 0 if the info is not available.
     */
    @SerialName("bodySize") var bodySize: Int,
) {
    
    /*
    constructor(onBeforeRedirectDetails: OnBeforeRedirectDetails) : this(
        onBeforeRedirectDetails.statusCode,
        onBeforeRedirectDetails.statusLine,
        "",
        cookies = mutableSetOf(),
        headers = mutableSetOf(),
        Content(0, "", null, null),
        onBeforeRedirectDetails.redirectUrl,
        0,
        0,
    ) {
        handleResponseHeaders(onBeforeRedirectDetails.responseHeaders)
    }
    */
    
    constructor(onAuthRequiredDetails: OnAuthRequiredDetails) : this(
        onAuthRequiredDetails.statusCode,
        onAuthRequiredDetails.statusLine,
        "",
        cookies = mutableSetOf(),
        headers = mutableSetOf(),
        Content(0, "", null, null),
        "",
        0,
        0,
    ) {
        handleResponseHeaders(onAuthRequiredDetails.responseHeaders)
    }
    
    constructor(onHeadersReceivedDetails: OnHeadersReceivedDetails) : this(
        onHeadersReceivedDetails.statusCode,
        onHeadersReceivedDetails.statusLine,
        "",
        cookies = mutableSetOf(),
        headers = mutableSetOf(),
        Content(0, "", null, null),
        "",
        0,
        0,
    ) {
        handleResponseHeaders(onHeadersReceivedDetails.responseHeaders)
    }
    
    constructor(onErrorOccurredDetails: OnErrorOccurredDetails) : this(
        0,
        onErrorOccurredDetails.error,
        "",
        cookies = mutableSetOf(),
        headers = mutableSetOf(),
        Content(0, "", null, null),
        "",
        0,
        0,
    )
    
    init {
        log("< $status $statusText")
    }
    
    fun handleResponseHeaders(responseHeaders: Array<HttpHeader>?) {
        if (responseHeaders == null) {
            return
        }
        
        fillInHeaders(responseHeaders)
        fillInCookies(responseHeaders)
    }
    
    private fun fillInCookies(responseHeaders: Array<HttpHeader>) {
        val newCookies = responseHeaders.asSequence()
            .filter { it.name.contains("Cookie", ignoreCase = true) }
            .flatMap { HttpCookie.parse(it.value) }
            .map(::Cookie)
            .toList()
        val modified = cookies.addAll(newCookies)
        if (modified) log("Got cookies: ${newCookies.size} new, ${newCookies.size} total")
    }
    
    private fun fillInHeaders(responseHeaders: Array<HttpHeader>) {
        val modified = headers.addAll(responseHeaders.map(::Header))
        redirectURL = responseHeaders.find { it.name.lowercase() == "location" }?.value ?: ""
        if (modified) responseHeaders.forEach { log("< ${it.name}: ${it.value}") }
    }
    
    fun setContent(contentString: String) {
        var mimeType = headers.find { it.name.lowercase() == "content-type" }?.value
        
        val encodedContent: String
        val encoding: String?
        if (contentString.looksLikeBinaryData(0.2)) {
            encodedContent = Base64.encodeToString(contentString.toByteArray(), Base64.DEFAULT)
            encoding = "base64"
            if (mimeType == null) {
                mimeType = "application/octet-stream"
            }
        } else {
            encodedContent = contentString
            encoding = null
            if (mimeType == null) {
                mimeType = "text/plain"
            }
        }
        
        content = Content(
            contentString.length,
            mimeType,
            encodedContent,
            encoding,
        )
    }
}

fun String.looksLikeBinaryData(threshold: Double): Boolean {
    val totalChars = length
    var nonAsciiChars = 0
    
    for (char in this) {
        if (char.code !in 0..127) {
            nonAsciiChars++
        }
    }
    
    return (nonAsciiChars.toDouble() / totalChars) > threshold
}
