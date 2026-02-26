package de.binarynoise.liberator

import java.util.concurrent.TimeUnit.*
import de.binarynoise.liberator.portals.allPortalLiberators
import de.binarynoise.liberator.portals.allPortalRedirectors
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup

class Liberator(
    private val clientInit: (OkHttpClient.Builder) -> Unit,
    val portalTestUrl: PortalTestURL,
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
                        log("> ${it.body.contentType() ?: it.headers?.get("Content-Disposition")} (${it.body.contentLength()} bytes)")
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
        val response = client.get(portalTestUrl.httpUrl, null)
        
        val res = recurse(response, 0)
        
        if (res !is LiberationResult.Success) {
            return res
        }
        
        var portalResponse: Response? = null
        var count = 0
        while (count++ < 3) {
            Thread.sleep(1000)
            
            // check if the user is still in the portal
            val (httpIsInPortal, redirectedResponse) = isInPortal(portalTestUrl.httpUrl)
            portalResponse = redirectedResponse
            if (httpIsInPortal) continue
            
            tryOrIgnore {
                val (httpsIsInPortal, redirectedResponse) = isInPortal(portalTestUrl.httpsUrl)
                portalResponse = redirectedResponse
                if (httpsIsInPortal) continue
            }
            
            return res
        }
        return LiberationResult.StillCaptured(portalResponse?.requestUrl.toString(), res.solvers)
    }
    
    private fun isInPortal(portalTestUrl: HttpUrl): Pair<Boolean, Response?> {
        val response = client.get(portalTestUrl, null)
        if (!response.isSuccessful) return Pair(true, null)
        val redirectedResponse = getRedirectedResponse(client, response, cookies)
        return Pair(redirectedResponse != null, redirectedResponse)
    }
    
    private fun recurse(response: Response, depth: Int): LiberationResult {
        try {
            val solvers: List<PortalLiberator> = allPortalLiberators //
                .filter { solver -> !solver.isExperimental() || PortalLiberatorConfig.experimental }
                .filter { solver -> !solver.ssidMustMatch() || (ssid != null && solver.ssidMatches(ssid)) }
                .filter { solver ->
                    try {
                        solver.canSolve(response)
                    } catch (e: Exception) {
                        log("failed to run canSolve for ${solver::class.simpleName}", e)
                        false
                    }
                }
            log("found ${solvers.size} solvers")
            
            if (solvers.isEmpty()) {
                
                val redirectedResponse = getRedirectedResponse(client, response, cookies)
                log("redirectedResponse.requestUrl: ${redirectedResponse?.requestUrl}")
                if (redirectedResponse == null) {
                    if (isCaptivePortalTestUrl(response.requestUrl) && response.isSuccessful) {
                        return LiberationResult.NotCaught
                    }
                    log("unknown captive portal: ${response.requestUrl}")
                    return LiberationResult.UnknownPortal(response.requestUrl.toString())
                }
                
                // follow redirects and try again
                check(depth < 10) { "too many redirects" }
                return recurse(redirectedResponse, depth + 1)
            }
            
            solvers.map { solver ->
                runCatching {
                    log("solver ${solver::class.simpleName}")
                    solver.solve(client, response, cookies)
                    log("solver ${solver::class.simpleName} finished processing")
                    return@runCatching solver
                }
            }.successes().getOrElse { throwable ->
                val e = if (throwable is NoSuccessException) IllegalStateException(
                    "all PortalLiberators failed: " + throwable.message, throwable
                ) else throwable
                return LiberationResult.Error(
                    response.requestUrl.toString(),
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
            return LiberationResult.Error(response.requestUrl.toString(), e.message.orEmpty(), "", e)
        }
    }
    
    private fun isCaptivePortalTestUrl(url: HttpUrl): Boolean {
        return portalTestUrl == url
    }
    
    private fun getRedirectedResponse(client: OkHttpClient, response: Response, cookies: Set<Cookie>): Response? {
        val redirectors = (allPortalRedirectors + LocationRedirector) //
            .filter { redirector -> !redirector.isExperimental() || PortalLiberatorConfig.experimental }
            .filter { redirector -> !redirector.ssidMustMatch() || (ssid != null && redirector.ssidMatches(ssid)) }
            .filter { redirector -> !redirector.requiresSuccess || response.code in 200..399 }
            .filter { redirector ->
                try {
                    redirector.canRedirect(response)
                } catch (e: Exception) {
                    log("failed to run canRedirect for ${redirector::class.simpleName}", e)
                    false
                }
            }
        log("found ${redirectors.size} redirectors")
        return redirectors.asSequence().map { redirector ->
            runCatching {
                redirector.redirect(client, response, cookies)
            }
        }.firstSuccess().getOrNull()
    }
    
    private object LocationRedirector : PortalRedirector {
        override fun canRedirect(response: Response): Boolean {
            val location = response.getLocation()
            return !location.isNullOrBlank()
        }
        
        override fun redirect(
            client: OkHttpClient,
            response: Response,
            cookies: Set<Cookie>,
        ): Response {
            return client.get(response.requestUrl, response.getLocation()!!)
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
