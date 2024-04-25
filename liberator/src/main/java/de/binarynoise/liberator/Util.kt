@file:OptIn(ExperimentalContracts::class)

package de.binarynoise.liberator

import java.lang.IllegalStateException
import java.net.URLDecoder
import java.nio.charset.Charset
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Sends a GET request to the specified URL using the provided OkHttpClient.
 *
 * @param url The URL to send the request to. Can be null if context is provided.
 * @param context The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.get(
    url: String?,
    context: HttpUrl?,
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    val request = Request.Builder().apply {
        val urlBuilder = if (url == null) {
            context?.newBuilder() ?: throw Error("url and context cannot both be null")
        } else {
            context?.newBuilder(url) ?: url.toHttpUrl().newBuilder()
        }
        
        queryParameters.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        url(urlBuilder.build())
        
        preConnectSetup()
    }.build()
    
    return newCall(request).execute()
}

/**
 * Sends a POST request to the specified URL using the provided OkHttpClient.
 *
 * @param url The URL to send the request to. Can be null if context is provided.
 * @param context The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param content The key-value pairs to include in the request body. Defaults to an empty map.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.post(
    url: String?,
    context: HttpUrl?,
    content: Map<String, String> = emptyMap(),
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    val request = Request.Builder().apply {
        val urlBuilder = if (url == null) {
            context?.newBuilder() ?: throw Error("url and context cannot both be null")
        } else {
            context?.newBuilder(url) ?: url.toHttpUrl().newBuilder()
        }
        queryParameters.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        url(urlBuilder.build())
        
        val formBodyBuilder = FormBody.Builder()
        content.forEach { (key, value) ->
            formBodyBuilder.add(key, value)
        }
        post(formBodyBuilder.build())
        
        preConnectSetup()
    }.build()
    
    return newCall(request).execute()
}

/**
 * Checks if the response is successful by checking the HTTP status code.
 * If the response is not successful, an exception is thrown with the corresponding error message.
 * If the response contains a "Location" header, the path of the resolved URL is checked for "401" or "403".
 * If the path contains either "401" or "403", an exception is thrown with the corresponding error message.
 *
 * @throws IllegalStateException if the response is not successful or if the path contains "401" or "403"
 */
fun Response.checkSuccess() {
    check(code in 200..399) {
        "HTTP error: $code $message"
    }
    val location = getLocation(skipStatusCheck = true)
    if (location != null) {
        val path = request.url.resolveOrThrow(location).decodedPath
        val pathContains40x = arrayOf("401", "403").any { path.contains(it) }
        check(!pathContains40x) {
            "Redirect to 401 or 403: $path"
        }
    }
}

/**
 * Retrieves the redirect location from the HTTP response.
 *
 * @param skipStatusCheck If true, skips the check for a successful HTTP status code. Default is false.
 * @return The redirect URL from the response header or parsed from the HTML if present, null otherwise.
 */
fun Response.getLocation(skipStatusCheck: Boolean = false): String? {
    if (!skipStatusCheck) checkSuccess()
    val header = header("Location")
    if (header != null) return header
    
    // parse html for redirect
    val html = parseHtml(skipStatusCheck = true)
    val meta = html.selectFirst("""meta[http-equiv="refresh"]""")
    if (meta != null) {
        return meta.attr("content").substringAfter(';').substringAfter('=').trim()
    }
    return null
}

/**
 * Parses the HTML response and returns a jsoup Document object.
 *
 * @param skipStatusCheck If true, skips the check for a successful HTTP status code. Default is false.
 * @return The parsed HTML as a Document object.
 */
fun Response.parseHtml(skipStatusCheck: Boolean = false): Document {
    if (!skipStatusCheck) checkSuccess()
    return Jsoup.parse(readText(skipStatusCheck = true), request.url.toString())
}

/**
 * Reads the response body into a string.
 *
 * @param skipStatusCheck If true, skips the check for a successful HTTP status code. Default is false.
 * @return The response body as a string.
 */
fun Response.readText(skipStatusCheck: Boolean = false): String {
    if (!skipStatusCheck) checkSuccess()
    val source = body?.source() ?: return ""
    val charset: Charset = body?.contentType()?.charset() ?: Charsets.UTF_8
    source.request(Long.MAX_VALUE)
    return source.buffer.clone().readString(charset)
}

val Response.requestUrl: HttpUrl
    get() = this.request.url

val HttpUrl.decodedPath: String
    get() = URLDecoder.decode(encodedPath, "UTF-8")

val HttpUrl.firstPathSegment
    get() = pathSegments.firstOrNull()

/**
 * Resolves a new path relative to the given HttpUrl and throws an IllegalArgumentException if the resulting URL is not well-formed.
 *
 * @param newPath the new path to resolve
 * @return the resolved HttpUrl
 * @throws IllegalArgumentException if the resulting URL is not well-formed
 */
fun HttpUrl.resolveOrThrow(newPath: String): HttpUrl =
    newBuilder(newPath)?.build() ?: throw IllegalArgumentException("constructed not well-formed url: $this -> $newPath")
