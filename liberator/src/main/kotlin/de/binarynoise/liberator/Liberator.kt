package de.binarynoise.liberator

import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.TimeUnit.MINUTES
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.api.json.har.Browser
import de.binarynoise.captiveportalautologin.api.json.har.Cache
import de.binarynoise.captiveportalautologin.api.json.har.Creator
import de.binarynoise.captiveportalautologin.api.json.har.Entry
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Log
import de.binarynoise.captiveportalautologin.api.json.har.Timings
import de.binarynoise.liberator.portals.allPortalLiberators
import de.binarynoise.liberator.portals.allPortalRedirectors
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class Liberator(
    private val clientInit: (OkHttpClient.Builder) -> Unit,
    val portalTestUrl: PortalTestURL,
    private val userAgent: String,
    private val ssid: String?,
    private val experimental: Boolean = false,
    appVersion: String = "",
    liberatorVersion: String = "",
    private val requestSystemReevaluation: () -> Unit = {},
    private val isSystemLiberated: () -> Boolean = { false },
) {
    
    private val cookies: MutableSet<Cookie> = mutableSetOf()
    
    private val creator = Creator("CaptivePortalAutoLogin", appVersion)
    private val browser = Browser("Liberator", liberatorVersion)
    
    private val entries = mutableListOf<Entry>()
    
    private val log = Log("1.2", creator, browser, mutableListOf(), entries)
    private val har = HAR(log)
    
    
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
     * - save cookies,
     * - record HAR entries
     */
    private fun interceptRequest(chain: Interceptor.Chain): Response {
        
        val originalRequest = chain.request()
        val cookiesToSend = cookies.filter { it.matches(originalRequest.url) }
        val newRequest = originalRequest.newBuilder().apply {
            header("User-Agent", userAgent)
            header("Connection", "Keep-Alive")
            log("Loading cookies for ${originalRequest.url}: ${cookiesToSend.joinToString { "${it.name}=${it.value}" }}")
            if (cookiesToSend.isNotEmpty()) {
                val cookieHeader = cookiesToSend.joinToString(separator = "; ") { "${it.name}=${it.value}" }
                header("Cookie", cookieHeader)
            }
        }.build()
        
        val harRequest = Request(newRequest, cookiesToSend)
        
        val startTime = Clock.System.now()
        val response = chain.proceed(newRequest)
        val fromCache = response.sentRequestAtMillis < startTime.toEpochMilliseconds()
        
        val harResponse = parseResponse(response, cookies)
        
        entries.add(
            Entry(
            null,
            Instant.fromEpochMilliseconds(response.sentRequestAtMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
            harRequest,
            harResponse,
            Cache(),
            Timings(
                blocked = (response.sentRequestAtMillis - startTime.toEpochMilliseconds()).toInt()
                    .takeIf { !fromCache } ?: 0,
                receive = (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt()
                    .takeIf { !fromCache } ?: 0,
            ),
            null,
            null,
        ))
        
        return response
    }
    
    /**
     * Attempts to liberate the user by making a series of HTTP requests to the portal.
     */
    fun liberate(): LiberationResult {
        val (isInPortalPre, portalResponsePre) = isCaughtInPortal()
        if (!isInPortalPre) {
            return LiberationResult.NotCaught
        } else if (portalResponsePre == null) {
            log("unknown captive portal redirection")
            return LiberationResult.UnknownPortal(null.toString())
        }
        
        val liberationResult = recurse(portalResponsePre, 0)
        
        if (liberationResult !is LiberationResult.Success) {
            return liberationResult
        }
        
        val (isInPortalPost, portalResponsePost) = isCaughtInPortal(3, true)
        if (!isInPortalPost) {
            return liberationResult
        }
        if (liberationResult.solvers == SubmitOnlyForm::class.simpleName) {
            return LiberationResult.UnknownPortal(liberationResult.url)
        }
        return LiberationResult.StillCaptured(portalResponsePost?.requestUrl.toString(), liberationResult.solvers, har)
    }
    
    private fun isCaughtInPortal(
        maxTries: Int = 1,
        enableRequestSystemReevaluation: Boolean = false,
    ): Pair<Boolean, Response?> {
        var redirectedResponse: Response? = null
        var count = 0
        while (count++ < maxTries) {
            if (count > 1) Thread.sleep(1000)
            if (enableRequestSystemReevaluation) requestSystemReevaluation()
            if (enableRequestSystemReevaluation && isSystemLiberated()) return Pair(false, null)
            
            val (httpIsInPortal, redirectedResponseHttp) = isInPortal(portalTestUrl.httpUrl)
            redirectedResponse = redirectedResponseHttp
            
            try {
                val (httpsIsInPortal, redirectedResponseHttps) = isInPortal(portalTestUrl.httpsUrl)
                redirectedResponse = redirectedResponseHttps
                if (httpsIsInPortal) continue
            } catch (e: Exception) {
                // HTTPS errors mean we're (still) in the portal
                if (e is IOException || e is GeneralSecurityException) continue
                throw e
            }
            
            // all requests went through -> we're not in the portal (anymore)
            return Pair(false, null)
        }
        return Pair(true, redirectedResponse)
    }
    
    private fun isInPortal(portalTestUrl: HttpUrl): Pair<Boolean, Response?> {
        val response = client.get(portalTestUrl, null)
        val redirectedResponse = getRedirectedResponse(client, response, cookies)
        return Pair(!response.isSuccessful || redirectedResponse != null, redirectedResponse)
    }
    
    private fun recurse(response: Response, depth: Int): LiberationResult {
        try {
            val solvers: List<PortalLiberator> = allPortalLiberators //
                .filter { solver -> !solver.isExperimental() || experimental }
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
                    if (SubmitOnlyForm.canSolve(response)) try {
                        log("attempting to solve unknown portal with ${SubmitOnlyForm::class.simpleName}")
                        SubmitOnlyForm.solve(
                            client,
                            response,
                            LiberatorExtras(
                                cookies = cookies,
                                portalTestUrl = portalTestUrl,
                                userAgent = userAgent,
                            ),
                        )
                        log("solver ${SubmitOnlyForm::class.simpleName} finished processing")
                        return LiberationResult.Success(
                            response.requestUrl.toString(),
                            SubmitOnlyForm::class.simpleName!!,
                        )
                    } catch (e: Exception) {
                        log("solver ${SubmitOnlyForm::class.java} failed", e)
                    }
                    return LiberationResult.UnknownPortal(response.requestUrl.toString())
                }
                
                // follow redirects and try again
                check(depth < 10) { "too many redirects" }
                return recurse(redirectedResponse, depth + 1)
            }
            
            solvers.map { solver ->
                runCatching {
                    log("solver ${solver::class.simpleName}")
                    solver.solve(
                        client,
                        response,
                        LiberatorExtras(
                            cookies = cookies,
                            portalTestUrl = portalTestUrl,
                            userAgent = userAgent,
                        ),
                    )
                    log("solver ${solver::class.simpleName} finished processing")
                    return@runCatching solver
                }
            }.successes().getOrElse { throwable ->
                if (throwable is UnsupportedPortalException) {
                    return LiberationResult.UnsupportedPortal(response.requestUrl.toString())
                }
                val message = if (throwable is NoSuccessException) {
                    "all PortalLiberators failed: ${throwable.message}"
                } else throwable.message
                return LiberationResult.Error(
                    response.requestUrl.toString(),
                    message,
                    solvers.joinToString { it::class.simpleName!! },
                    throwable,
                    har,
                )
            }.forEach {
                log("liberated by ${it::class.simpleName}")
            }
            return LiberationResult.Success(
                response.requestUrl.toString(),
                solvers.joinToString { it::class.simpleName!! },
            )
        } catch (e: Exception) {
            return LiberationResult.Error(response.requestUrl.toString(), e.message, null, e, har)
        }
    }
    
    private fun isCaptivePortalTestUrl(url: HttpUrl): Boolean {
        return portalTestUrl == url
    }
    
    private fun getRedirectedResponse(client: OkHttpClient, response: Response, cookies: Set<Cookie>): Response? {
        val redirectors = (allPortalRedirectors + LocationRedirector) //
            .filter { redirector -> !redirector.isExperimental() || experimental }
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
                redirector.redirect(
                    client,
                    response,
                    LiberatorExtras(
                        cookies = cookies,
                        portalTestUrl = portalTestUrl,
                        userAgent = userAgent,
                    ),
                )
            }
        }.firstSuccess().getOrNull()
    }
    
    sealed class LiberationResult {
        data object NotCaught : LiberationResult()
        
        data class Success(val url: String, val solvers: String) : LiberationResult()
        data class Timeout(val url: String) : LiberationResult()
        data class Error(
            val url: String,
            val message: String?,
            val solvers: String?,
            val exception: Throwable,
            val har: HAR,
        ) : LiberationResult()
        
        data class UnknownPortal(val url: String) : LiberationResult()
        data class StillCaptured(val url: String, val solvers: String, val har: HAR) : LiberationResult()
        data class UnsupportedPortal(val url: String) : LiberationResult()
    }
}

object LocationRedirector : PortalRedirector {
    override fun canRedirect(response: Response): Boolean {
        val location = response.getLocation()
        return !location.isNullOrBlank()
    }
    
    override fun redirect(
        client: OkHttpClient,
        response: Response,
        extras: LiberatorExtras,
    ): Response {
        return client.get(response.requestUrl, response.getLocation()!!)
    }
}
