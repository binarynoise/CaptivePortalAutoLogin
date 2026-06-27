package de.binarynoise.liberator

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.api.json.har.Content
import de.binarynoise.captiveportalautologin.api.json.har.Cookie
import de.binarynoise.captiveportalautologin.api.json.har.Header
import de.binarynoise.captiveportalautologin.api.json.har.PostData
import de.binarynoise.captiveportalautologin.api.json.har.PostParam
import de.binarynoise.captiveportalautologin.api.json.har.Query
import de.binarynoise.captiveportalautologin.api.json.har.Request
import de.binarynoise.captiveportalautologin.api.json.har.Response
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.json.JsonObject
import de.binarynoise.util.json.prettyPrinter
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.queryEntries
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.FormBody
import okhttp3.MultipartBody
import org.jsoup.Jsoup
import okhttp3.Cookie as OkCookie
import okhttp3.Request as OkRequest
import okhttp3.RequestBody as OkRequestBody
import okhttp3.Response as OkResponse

fun Cookie(cookie: OkCookie) = Cookie(
    name = cookie.name,
    value = cookie.value,
    path = cookie.path,
    domain = cookie.domain,
    expires = Instant.fromEpochMilliseconds(cookie.expiresAt).toLocalDateTime(TimeZone.currentSystemDefault()),
    httpOnly = cookie.httpOnly,
    secure = cookie.secure,
)

fun Header(header: Pair<String, String>): Header {
    val (name, value) = header
    return Header(name, value)
}

fun PostData(body: OkRequestBody?): PostData? {
    if (body == null) return null
    
    return when (body) {
        is FormBody -> {
            val params = (0..<body.size).map { i ->
                val name = body.name(i)
                val value = body.value(i)
                log("> $name=$value")
                PostParam(name, value, null, null)
            }
            PostData(
                mimeType = "application/x-www-form-urlencoded",
                params = params,
                text = body.readText(),
            )
        }
        is MultipartBody -> {
            log("> Content-Type: ${body.contentType()}")
            val params = body.parts.mapNotNull { part ->
                val text = part.body.readText()
                val contentType = part.body.contentType()?.toString()
                val disposition = part.headers?.get("Content-Disposition") ?: return@mapNotNull null
                val name = disposition.split(";")
                    .find { it.trimStart().startsWith("name=") }
                    ?.substringAfter("name=")
                    ?.trim('"', ' ') ?: return@mapNotNull null
                val fileName = disposition.split(";")
                    .find { it.trimStart().startsWith("filename=") }
                    ?.substringAfter("filename=")
                    ?.trim('"', ' ')
                log("> ${contentType ?: disposition} (${part.body.contentLength()} bytes)")
                PostParam(name, text, fileName, contentType)
            }
            PostData(
                mimeType = body.contentType().toString(),
                params = params,
                text = body.readText(),
            )
        }
        else -> {
            log("> Content-Type: ${body.contentType()} (${body.contentLength()} bytes)")
            PostData(
                mimeType = body.contentType()?.toString() ?: "",
                params = null,
                text = body.readText(),
            )
        }
    }
}

fun Request(request: OkRequest, cookies: Collection<OkCookie>): Request {
    log("> ${request.method} ${request.url}")
    return Request(
        method = request.method,
        url = request.url.toString(),
        httpVersion = "",
        cookies = cookies.map(::Cookie).toMutableSet(),
        headers = request.headers.map(::Header).toMutableSet().onEach { log("> ${it.name}: ${it.value}") },
        queryString = request.url.queryEntries().map { (key, value) -> Query(key, value) }.toMutableList(),
        postData = PostData(request.body),
        headersSize = 0,
        bodySize = 0,
    )
}

fun Content(response: OkResponse): Content {
    var text = response.readText(skipStatusCheck = true)
    
    val contentType = response.header("Content-Type")
    if (contentType != null) when {
        contentType.startsWith("text/html") -> text = Jsoup.parse(text).html()
        contentType.startsWith("text/xml") -> text = Jsoup.parse(text).body().html()
        contentType.startsWith("application/json") -> text = prettyPrinter.encodeToString(JsonObject(text))
    }
    
    log(text)
    
    return Content(
        size = text.length.toLong(),
        mimeType = contentType ?: "",
        text = text,
        encoding = "",
    )
}

fun parseResponse(response: OkResponse, cookies: MutableSet<OkCookie>): Response {
    log("< ${response.code} ${response.message}")
    
    val newCookies = OkCookie.parseAll(response.requestUrl, response.headers)
    if (newCookies.isNotEmpty()) {
        log("Saving cookies for ${response.requestUrl}: ${newCookies.joinToString { "${it.name}=${it.value}" }}")
        newCookies.forEach { new ->
            val old = cookies.find { old -> old.name == new.name }
            if (old != null) {
                cookies -= old
            }
            cookies += new
        }
        log("All cookies now: ${cookies.joinToString { "${it.name}=${it.value}" }}")
    }
    
    return Response(
        status = response.code,
        statusText = response.message,
        httpVersion = response.protocol.toString(),
        cookies = newCookies.map(::Cookie).toMutableSet(),
        headers = response.headers.map(::Header).toMutableSet().onEach { log("< ${it.name}: ${it.value}") },
        content = Content(response),
        redirectURL = response.getLocation() ?: "",
        headersSize = 0,
        bodySize = 0,
    )
}
