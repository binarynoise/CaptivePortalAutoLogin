package de.binarynoise.util.okhttp

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import de.binarynoise.logger.Logger.log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class HttpStatusCodeException(val code: Int, message: String, val response: Response) :
    IllegalStateException("$code $message")


/**
 * Checks if the response is successful by checking the HTTP status code.
 * If the response is not successful, an exception is thrown with the corresponding error message.
 * If the response contains a "Location" header, the path of the resolved URL is checked for "401" or "403".
 * If the path contains either "401" or "403", an exception is thrown with the corresponding error message.
 *
 * @throws IllegalStateException if the response is not successful or if the path contains "401" or "403"
 */
fun Response.checkSuccess() {
    if (code !in 200..399) {
        readText(skipStatusCheck = true)
        throw HttpStatusCodeException(code, message, this)
    }
    val location = getLocationUnchecked()
    if (location != null) {
        val path = location.toHttpUrl(request.url).decodedPath
        val pathContains40x = arrayOf("401", "403").any { path.contains(it) }
        check(!pathContains40x) {
            "Redirect to 401 or 403: $path"
        }
    }
}

/**
 * Retrieves the redirect location from the HTTP response.
 *
 * Parses Location Header and `meta[http-equiv="refresh"]`
 *
 * @return The redirect URL from the response header or parsed from the HTML if present, null otherwise.
 */
fun Response.getLocation(): String? {
    checkSuccess()
    return getLocationUnchecked()
}

/**
 * Retrieves the redirect location from the HTTP response.
 *
 * Parses Location Header and `meta[http-equiv="refresh"]`
 *
 * @return The redirect URL from the response header or parsed from the HTML if present, null otherwise.
 */
fun Response.getLocationUrl(): HttpUrl? {
    return this.getLocation()?.toHttpUrl()
}

private fun Response.getLocationUnchecked(): String? {
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

/**
 * Parses the JSON response and returns a [JSONObject].
 *
 * @param skipStatusCheck If true, skips the check for a successful HTTP status code. Default is false.
 * @return The parsed JSON as a [JSONObject]
 */
fun Response.parseJsonObject(skipStatusCheck: Boolean = false): JSONObject {
    return JSONObject(this.readText(skipStatusCheck))
}

/**
 * Parses the JSON response and returns a [JSONArray].
 *
 * @param skipStatusCheck If true, skips the check for a successful HTTP status code. Default is false.
 * @return The parsed JSON as a [JSONArray]
 */
fun Response.parseJsonArray(skipStatusCheck: Boolean = false): JSONArray {
    return JSONArray(this.readText(skipStatusCheck))
}


private val cache = mutableMapOf<Response, String>()
private val executor = Executors.newSingleThreadScheduledExecutor {
    Thread(it, "okhttp-cache-daemon").apply {
        isDaemon = true
    }
}

fun ScheduledExecutorService.schedule(delay: Long, unit: TimeUnit, command: Runnable): ScheduledFuture<*> =
    schedule(command, delay, unit)

/**
 * Reads the response body into a string.
 *
 * The string is cached for 10 seconds.
 * It may only be read again while in the cache.
 *
 * @param skipStatusCheck If true, skips the check for a successful HTTP status code. Default is false.
 * @return The response body as a string.
 */
fun Response.readText(skipStatusCheck: Boolean = false): String {
    if (!skipStatusCheck) checkSuccess()
    
    return cache.getOrPut(this) {
        executor.schedule(10, TimeUnit.SECONDS) {
            cache.remove(this@readText)
        }
        body.string()
    }
}

/**
 * Returns the URL of the request this response is for.
 *
 * @return the URL of the request
 */
val Response.requestUrl: HttpUrl
    get() = request.url

/**
 * Follows redirects until the final non-redirect response is received.
 *
 * @param client the OkHttpClient to use for the redirects
 * @param followRedirect a predicate which takes in a [HttpUrl] and determines whether to follow the redirect or not
 * @return the final non-redirect response
 */
tailrec fun Response.followRedirects(
    client: OkHttpClient,
    followRedirect: (url: HttpUrl) -> Boolean = { _ -> true },
): Response {
    val location = getLocation()?.toHttpUrl(request.url) ?: return this
    
    if (!followRedirect(location)) return this
    
    log("following redirect: $requestUrl -> $location")
    
    val newRequest = request.newBuilder()
    newRequest.url(location)
    
    if (code != 307 && code != 308) {
        // unless requested to keep the http method, change it to GET
        newRequest.method("GET", null)
    }
    
    body.close()
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

fun Response.Builder.setLocation(location: String) = header("Location", location)

/**
 * Submit the only form present within this [Response]
 * @throws IllegalArgumentException if more than one form is present
 * @throws NoSuchElementException if no form is present
 */
fun Response.submitOnlyForm(
    client: OkHttpClient,
    parameters: Map<String, String> = emptyMap(),
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    val html = this.parseHtml()
    val baseUrl = this.requestUrl
    return html.submitOnlyForm(client, baseUrl, parameters, queryParameters, preConnectSetup)
}
