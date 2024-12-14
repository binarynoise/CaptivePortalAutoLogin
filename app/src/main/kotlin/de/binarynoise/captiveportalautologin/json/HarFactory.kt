@file:OptIn(ExperimentalEncodingApi::class)

package de.binarynoise.captiveportalautologin.json

import java.net.HttpCookie
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import android.os.Build
import de.binarynoise.captiveportalautologin.api.json.har.Content
import de.binarynoise.captiveportalautologin.api.json.har.Cookie
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Header
import de.binarynoise.captiveportalautologin.api.json.har.PostData
import de.binarynoise.captiveportalautologin.api.json.har.PostParam
import de.binarynoise.captiveportalautologin.api.json.har.Request
import de.binarynoise.captiveportalautologin.api.json.har.Response
import de.binarynoise.captiveportalautologin.json.webRequest.HttpHeader
import de.binarynoise.captiveportalautologin.json.webRequest.OnAuthRequiredDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnBeforeRequestDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnErrorOccurredDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnHeadersReceivedDetails
import de.binarynoise.captiveportalautologin.json.webRequest.RequestBody
import de.binarynoise.logger.Logger.log

fun Header(httpHeader: HttpHeader): Header = Header(httpHeader.name, httpHeader.value ?: "")

fun Cookie(cookie: HttpCookie): Cookie = Cookie(
    name = cookie.name,
    value = cookie.value,
    path = cookie.path,
    domain = cookie.domain,
    expires = Instant.fromEpochMilliseconds(cookie.maxAge).toLocalDateTime(TimeZone.currentSystemDefault()),
    httpOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cookie.isHttpOnly else false,
    secure = cookie.secure
)

fun HAR.toJson(): String {
    return serializer.encodeToString(this)
}

fun Request(onBeforeRequestDetails: OnBeforeRequestDetails) = Request(
    onBeforeRequestDetails.method,
    onBeforeRequestDetails.url,
    "",
    cookies = kotlin.collections.mutableSetOf(),
    headers = kotlin.collections.mutableSetOf(),
    query = kotlin.collections.mutableListOf(),
    postData = null,
    0,
    0,
).apply {
    log("> $method $url")
    
    fillInPostData(onBeforeRequestDetails.requestBody)
}

fun Request.handleRequestHeaders(requestHeaders: Array<HttpHeader>?) {
    if (requestHeaders == null) {
        return
    }
    
    fillInHeaders(requestHeaders)
    fillInCookies(requestHeaders)
}

fun Request.fillInHeaders(requestHeaders: Array<HttpHeader>) {
    val modified = headers.addAll(requestHeaders.map(::Header))
    if (modified) requestHeaders.forEach { log("> ${it.name}: ${it.value}") }
}

fun Request.fillInCookies(requestHeaders: Array<HttpHeader>) {
    val newCookies = requestHeaders.asSequence()
        .filter { it.name.contains("Cookie", ignoreCase = true) }
        .flatMap { HttpCookie.parse(it.value) }
        .map(::Cookie)
        .toList()
    val modified = cookies.addAll(newCookies)
    if (modified) log("Filled in cookies: ${newCookies.size} new, ${cookies.size} total")
}

fun Request.fillInPostData(body: RequestBody?) = with(body) {
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
    if (raw.isEmpty()) {
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
            val encodedContent = Base64.encode(contentString.toByteArray())
            PostParam(i.toString(), encodedContent, data.file, "application/octet-stream")
        } else {
            PostParam(i.toString(), contentString, data.file, "text/plain")
        }
    }
    
    postData = PostData(
        "multipart/form-data",
        params,
        params.joinToString("\n\n"),
    )
    log("Filled in POST data (${params.size} params)")
}

fun Response(onAuthRequiredDetails: OnAuthRequiredDetails) = Response(
    onAuthRequiredDetails.statusCode,
    onAuthRequiredDetails.statusLine,
    "",
    cookies = mutableSetOf(),
    headers = mutableSetOf(),
    Content(0, "", null, null),
    "",
    0,
    0,
).apply {
    log("< $status $statusText")
    handleResponseHeaders(onAuthRequiredDetails.responseHeaders)
}

fun Response(onHeadersReceivedDetails: OnHeadersReceivedDetails) = Response(
    onHeadersReceivedDetails.statusCode,
    onHeadersReceivedDetails.statusLine,
    "",
    cookies = mutableSetOf(),
    headers = mutableSetOf(),
    Content(0, "", null, null),
    "",
    0,
    0,
).apply {
    log("< $status $statusText")
    handleResponseHeaders(onHeadersReceivedDetails.responseHeaders)
}

fun Response(onErrorOccurredDetails: OnErrorOccurredDetails) = Response(
    0,
    onErrorOccurredDetails.error,
    "",
    cookies = mutableSetOf(),
    headers = mutableSetOf(),
    Content(0, "", null, null),
    "",
    0,
    0,
).apply {
    log("< $status $statusText")
    
}

fun Response.handleResponseHeaders(responseHeaders: Array<HttpHeader>?) {
    if (responseHeaders == null) {
        return
    }
    
    fillInHeaders(responseHeaders)
    fillInCookies(responseHeaders)
}

fun Response.fillInCookies(responseHeaders: Array<HttpHeader>) {
    val newCookies = responseHeaders.asSequence()
        .filter { it.name.contains("Cookie", ignoreCase = true) }
        .flatMap { it.value?.lineSequence() ?: emptySequence() }
        .flatMap {
            try {
                HttpCookie.parse(it)
            } catch (e: Exception) {
                try {
                    log("Failed to parse cookie: ${it}, trying fallback")
                    HttpCookie.parse(it.substringBefore(";"))
                } catch (e: Exception) {
                    log("Failed to parse cookie, fallback failed", e)
                    emptyList<HttpCookie>()
                }
            }
        }
        .map(::Cookie)
        .toList()
    val modified = cookies.addAll(newCookies)
    if (modified) log("Got cookies: ${newCookies.size} new, ${newCookies.size} total")
}

fun Response.fillInHeaders(responseHeaders: Array<HttpHeader>) {
    redirectURL = responseHeaders.find { it.name.lowercase() == "location" }?.value ?: ""
    val modified = headers.addAll(responseHeaders.map(::Header))
    if (modified) responseHeaders.forEach { log("< ${it.name}: ${it.value}") }
}

fun Response.setContent(contentString: String) {
    var mimeType = headers.find { it.name.lowercase() == "content-type" }?.value
    
    val encodedContent: String
    val encoding: String?
    if (contentString.looksLikeBinaryData(0.2)) {
        encodedContent = Base64.encode(contentString.toByteArray())
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
