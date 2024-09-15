@file:OptIn(ExperimentalContracts::class)

package de.binarynoise.util.okhttp

import java.net.URLDecoder
import java.nio.charset.Charset
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import de.binarynoise.logger.Logger.log
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

/**
 * Media type for JSON with UTF-8 character set for sending JSON data.
 */
val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

/**
 * Sends a GET request to the specified URL using the provided OkHttpClient.
 *
 * @param url The URL to send the request to. Can be null if context is provided.
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.get(
    base: HttpUrl?,
    url: String?,
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    val request = Request.Builder().apply {
        val urlBuilder = if (url == null) {
            base?.newBuilder() ?: throw Error("url and context cannot both be null")
        } else {
            base?.newBuilder(url) ?: url.toHttpUrl().newBuilder()
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
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.call(
    base: HttpUrl?,
    url: String?,
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    val request = Request.Builder().apply {
        val urlBuilder = if (url == null) {
            base?.newBuilder() ?: throw Error("url and context cannot both be null")
        } else {
            base?.newBuilder(url) ?: url.toHttpUrl().newBuilder()
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
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param json The JSON string to include in the request body.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.postJson(
    base: HttpUrl?,
    url: String?,
    json: String,
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    return call(base, url, queryParameters) {
        post(json.toRequestBody(MEDIA_TYPE_JSON))
        preConnectSetup()
    }
}

/**
 * Sends a POST request to the specified URL using the provided OkHttpClient.
 *
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param json The JSON string to include in the request body.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.putJson(
    base: HttpUrl?,
    url: String?,
    json: String,
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    return call(base, url, queryParameters) {
        put(json.toRequestBody(MEDIA_TYPE_JSON))
        preConnectSetup()
    }
}

/**
 * Sends a POST request to the specified URL using the provided OkHttpClient.
 *
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param form The key-value pairs to include in the request body for form-encoded data.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.postForm(
    base: HttpUrl?,
    url: String?,
    form: Map<String, String>,
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    return call(base, url, queryParameters) {
        val formBodyBuilder = FormBody.Builder()
        form.forEach { (key, value) ->
            formBodyBuilder.add(key, value)
        }
        post(formBodyBuilder.build())
        
        preConnectSetup()
    }
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
 * Parses `<WISPAccessGatewayParam>`, Location Header and `meta[http-equiv="refresh"]`
 *
 * @param skipStatusCheck If true, skips the check for a successful HTTP status code. Default is false.
 * @return The redirect URL from the response header or parsed from the HTML if present, null otherwise.
 */
fun Response.getLocation(skipStatusCheck: Boolean = false): String? {
    if (!skipStatusCheck) checkSuccess()
    
    val html = parseHtml(skipStatusCheck = true)
    
    val header = header("Location")
    if (header != null) return header
    
    val meta = html.selectFirst("""meta[http-equiv="refresh"]""")
    if (meta != null) {
        val metaUrl = meta.attr("content").substringAfter(';').substringAfter('=').trim()
        return metaUrl
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

fun Document.getInput(name: String) = selectFirst("input[name=$name]")?.attr("value") ?: error("no $name")

/**
 * Reads the response body into a string.
 *
 * @param skipStatusCheck If true, skips the check for a successful HTTP status code. Default is false.
 * @return The response body as a string.
 */
fun Response.readText(skipStatusCheck: Boolean = false): String {
    if (!skipStatusCheck) checkSuccess()
    return body?.readText() ?: ""
}

fun ResponseBody.readText(): String {
    val source = this.source()
    val charset: Charset = this.contentType()?.charset() ?: Charsets.UTF_8
    source.request(Long.MAX_VALUE)
    return source.buffer.clone().readString(charset)
}

fun RequestBody.readText(): String {
    val charset: Charset = this.contentType()?.charset() ?: Charsets.UTF_8
    val buffer = Buffer()
    writeTo(buffer)
    return buffer.readString(charset)
}

/**
 * Returns the URL of the request this response is for.
 *
 * @return the URL of the request
 */
val Response.requestUrl: HttpUrl
    get() = this.request.url

/**
 * Decodes the path of the HttpUrl.
 *
 * @return the decoded path of the HttpUrl
 */
val HttpUrl.decodedPath: String
    get() = URLDecoder.decode(encodedPath, "UTF-8")

/**
 * Returns the first path segment of the HttpUrl.
 *
 * @return the first path segment of the HttpUrl, or null if the path is empty
 */
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

/**
 * Follows redirects until the final non-redirect response is received.
 *
 * @param client the OkHttpClient to use for the redirects
 * @return the final non-redirect response
 */
tailrec fun Response.followRedirects(client: OkHttpClient): Response {
    val location = this.getLocation(false) ?: return this
    
    log("following redirect: ${this.requestUrl} -> $location")
    
    val newRequest = this.request.newBuilder()
    newRequest.url(request.url.resolveOrThrow(location))
    
    if (code != 307 && code != 308) {
        // unless requested to keep the http method, change it to GET
        newRequest.method("GET", null)
    }
    
    val newResponse: Response = client.newCall(newRequest.build()).execute()
    
    return newResponse.followRedirects(client)
}

fun createDummyResponse(): Response.Builder = Response.Builder().apply {
    code(200)
    request(Request.Builder().url("http://_").build())
    protocol(Protocol.HTTP_1_0)
    message("OK")
    body("".toResponseBody())
}

fun Response.Builder.setLocation(location: String) = this.header("Location", location)

/**
 * Decodes HTML entities in the given string.
 *
 * @receiver The string to decode.
 * @return The decoded string.
 */
fun String.decodeHtml(): String = Parser.unescapeEntities(this, false)

/**
 * Decodes URL-encoded characters in the given string.
 *
 * @receiver The string to decode.
 * @return The decoded string.
 */
fun String.decodeUrl(): String = URLDecoder.decode(this, "UTF-8")
