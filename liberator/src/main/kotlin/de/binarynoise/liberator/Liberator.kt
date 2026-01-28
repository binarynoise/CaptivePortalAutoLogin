package de.binarynoise.liberator

import java.util.concurrent.TimeUnit.*
import de.binarynoise.liberator.portals.allPortalLiberators
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.resolveOrThrow
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup

class Liberator(
    private val clientInit: (OkHttpClient.Builder) -> Unit,
    val portalTestUrl: String,
    private val userAgent: String,
    private val ssid: String?,
) {
    
    private val cookies: MutableSet<Cookie> = mutableSetOf()
    
    private val client = OkHttpClient.Builder().apply {
        cache(null)
        retryOnConnectionFailure(true)
        followRedirects(false) // we do that manually if needed
//        followSslRedirects(true) // doesn't work as followRedirects is set to false
        
        addInterceptor(::interceptRequest)
        readTimeout(1, MINUTES)
        clientInit(this)
    }.build()
    
    /**
     * Intercepts the request, to
     * - add User-Agent, Connection and Cookie headers,
     * - log request details and POST request body,
     * - proceed with the request,
     * - log the response details and body,
     * - save cookies
     */
    private fun interceptRequest(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder().apply {
            header("User-Agent", userAgent)
            header("Connection", "Keep-Alive")
            val cookiesToSend = cookies.filter { it.matches(originalRequest.url) }
            log("Loading cookies for ${originalRequest.url}: ${cookiesToSend.joinToString { "${it.name}=${it.value}" }}")
            if (cookiesToSend.isNotEmpty()) {
                val cookieHeader = cookiesToSend.joinToString(separator = "; ") { "${it.name}=${it.value}" }
                header("Cookie", cookieHeader)
            }
        }.build()
        
        log("> ${newRequest.method} ${newRequest.url}")
        newRequest.headers.forEach { (name, value) ->
            log("> $name: $value")
        }
        if (newRequest.method == "POST") {
            when (val body = newRequest.body) {
                null -> {
                }
                is FormBody -> {
                    for (i in 0..<body.size) {
                        val name = body.name(i)
                        val value = body.value(i)
                        log("> $name=$value")
                    }
                }
                is MultipartBody -> {
                    log("> Content-Type: ${body.contentType()}")
                    body.parts.forEach {
                        log("> ${it.body.contentType()} (${body.contentLength()} bytes)")
                        log(it.body.readText())
                    }
                }
                else -> {
                    log("> Content-Type: ${body.contentType()} (${body.contentLength()} bytes)")
                    log(body.readText())
                }
            }
        }
        
        val response = chain.proceed(newRequest)
        
        log("< ${response.code} ${response.message}")
        response.headers.forEach { (name, value) ->
            log("< $name: $value")
        }
        var text = response.readText(skipStatusCheck = true)
        
        val newCookies = Cookie.parseAll(newRequest.url, response.headers)
        if (newCookies.isNotEmpty()) {
            log("Saving cookies for ${newRequest.url}: ${newCookies.joinToString { "${it.name}=${it.value}" }}")
            newCookies.forEach { new ->
                val old = cookies.find { old -> old.name == new.name }
                if (old != null) {
                    cookies -= old
                }
                cookies += new
            }
            log("All cookies now: ${cookies.joinToString { "${it.name}=${it.value}" }}")
        }
        
        // prettify text if html, xml or json
        val contentType = response.header("Content-Type")
        if (contentType != null) when {
            contentType.startsWith("text/html") -> text = Jsoup.parse(text).html()
            contentType.startsWith("text/xml") -> text = Jsoup.parse(text).body().html()
            contentType.startsWith("application/json") -> text = JSONObject(text).toString(2)
        }
        
        log(text)
        
        return response
    }
    
    /**
     * Attempts to liberate the user by making a series of HTTP requests to the portal.
     */
    fun liberate(): LiberationResult {
        val response = client.get(null, portalTestUrl)
        if (response.getLocation().isNullOrBlank()) {
            return LiberationResult.NotCaught
        }
        
        val res = recurse(response, 0)
        
        if (res !is LiberationResult.Success) {
            return res
        }
        
        var location: String? = ""
        var count = 0
        while (location != null && count++ < 3) {
            Thread.sleep(1000)
            
            // check if the user is still in the portal, try both http and https to avoid false positives
            location = client.get(null, portalTestUrl).getLocation() //
                ?: client.get(null, portalTestUrl.replace("http:", "https:")).getLocation()
        }
        return if (location.isNullOrBlank()) {
            res
        } else {
            LiberationResult.StillCaptured(location, res.solvers)
        }
    }
    
    private fun recurse(responseWithRedirect: Response, depth: Int): LiberationResult {
        val locationUrl = try {
            val location = responseWithRedirect.getLocation()
            if (location.isNullOrBlank()) return LiberationResult.UnknownPortal(responseWithRedirect.requestUrl.toString())
            
            responseWithRedirect.requestUrl.resolveOrThrow(location)
        } catch (e: Exception) {
            return LiberationResult.Error(responseWithRedirect.requestUrl.toString(), e.message.orEmpty(), "", e)
        }
        log("locationUrl: $locationUrl")
        try {
            val response = client.get(locationUrl, null)
            
            val solvers: List<PortalLiberator> = allPortalLiberators //
                .filter { solver -> PortalLiberatorConfig.experimental || !solver.isExperimental() }
                .filter { solver -> !solver.ssidMustMatch() || (ssid != null && solver.ssidMatches(ssid)) }
                .filter { solver ->
                    try {
                        solver.canSolve(response)
                    } catch (e: Exception) {
                        log("failed to run can solve for ${solver::class.simpleName}", e); false
                    }
                }
            log("found ${solvers.size}")
            
            if (solvers.isEmpty()) {
                log("unknown captive portal: $locationUrl")
                
                // follow redirects and try again
                check(depth < 10) { "too many redirects" }
                return recurse(response, depth + 1)
            }
            
            solvers.map { solver ->
                runCatching {
                    log("solver ${solver::class.simpleName}")
                    solver.solve(client, response, cookies)
                    log("solver ${solver::class.simpleName} finished processing")
                }
            }.successes().getOrElse { throwable ->
                val e = IllegalStateException("all PortalLiberators failed:" + throwable.message, throwable)
                return LiberationResult.Error(
                    locationUrl.toString(),
                    e.message.orEmpty(),
                    solvers.joinToString { it::class.simpleName.orEmpty() },
                    throwable,
                )
            }.forEach {
                log("liberated by ${it::class.simpleName}")
            }
            return LiberationResult.Success(
                response.requestUrl.toString(),
                solvers.joinToString { it::class.simpleName.orEmpty() },
            )
        } catch (e: Exception) {
            return LiberationResult.Error(locationUrl.toString(), e.message.orEmpty(), "", e)
        }
    }
    
    sealed class LiberationResult {
        data object NotCaught : LiberationResult()
        
        data class Success(val url: String, val solvers: String) : LiberationResult()
        data class Timeout(val url: String) : LiberationResult()
        data class Error(val url: String, val message: String, val solvers: String, val exception: Throwable) :
            LiberationResult()
        
        data class UnknownPortal(val url: String) : LiberationResult()
        data class StillCaptured(val url: String, val solvers: String) : LiberationResult()
        data class UnsupportedPortal(val url: String) : LiberationResult()
    }
}
