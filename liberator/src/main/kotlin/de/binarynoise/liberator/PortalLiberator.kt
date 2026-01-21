package de.binarynoise.liberator

import de.binarynoise.logger.Logger.log
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

object PortalLiberatorConfig {
    /**
     * Enables additional liberators that are considered experimental.
     */
    var experimental: Boolean = false
        set(value) {
            field = value
            log("PortalLiberatorConfig.debug = $value")
        }
}

/**
 * Marker interface for portal liberators.
 * 
 * Classes implementing this interface will be automatically discovered and included
 * in the portal liberation process.
 */
interface PortalLiberator {
    /**
     * Determines if this liberator can handle the given captive portal.
     * 
     * This method is called during portal detection to determine if this liberator
     * has the capability to authenticate with the specific captive portal system.
     *
     * The [response] is the HTTP response loaded from `response.requestUrl` containing the portal content.
     * 
     * @return true if this liberator can solve the portal, false otherwise.
     */
    fun canSolve(response: Response): Boolean
    
    /**
     * Attempts to solve the captive portal by performing the necessary authentication actions.
     * 
     * This method is called after [canSolve] has returned true, indicating that this liberator
     * can handle the detected portal. The implementation should perform all necessary HTTP
     * requests and form submissions to authenticate the user with the captive portal.
     * 
     * The [client] is the OkHttpClient instance to use for making HTTP requests to the portal.
     * The [response] is the HTTP response that contains the portal this PortalLiberator wants to solve.
     * The [cookies] is a read-only view of the current set of cookies.
     */
    fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>)
}

fun PortalLiberator.isExperimental(): Boolean = this::class.java.annotations.any { it is Experimental }
fun PortalLiberator.ssidMustMatch(): Boolean =
    this::class.java.annotations.filterIsInstance<SSID>().any { it.mustMatch }

fun PortalLiberator.ssidMatches(ssid: String): Boolean = this::class.java.annotations.filterIsInstance<SSID>()
    .flatMap { it.ssid.asIterable() }
    .partition { it.startsWith("/") && it.endsWith("/") }
    .let { (regexes, fixed) ->
        ssid in fixed || regexes.any { it.substring(1, it.length - 1).toRegex().matches(ssid) }
    }

/**
 * Annotation to specify which Wi-Fi networks (SSIDs) a portal liberator is designed for.
 * 
 * This annotation is either informational ([mustMatch] is false, default) 
 * or used to restrict portal liberator selection
 * to specific networks when [mustMatch] is true.
 * 
 * The SSID matching supports both exact string matches and regex patterns.
 * Values surrounded by forward slashes (e.g., "/pattern/")
 * are treated as regex patterns, while other values are treated as exact matches.
 */
@Target(AnnotationTarget.CLASS)
annotation class SSID(vararg val ssid: String, val mustMatch: Boolean = false)

/**
 * Annotation to mark portal liberators as verified and tested.
 * 
 * This annotation indicates that the liberator has been confirmed
 * to work correctly with the target captive portals.
 */
@Target(AnnotationTarget.CLASS)
annotation class Verified

/**
 * Annotation to mark portal liberators as experimental.
 * 
 * This annotation indicates that the liberator is under development and is
 * not yet considered production ready. Experimental liberators are not
 * automatically enabled and must be explicitly enabled by setting
 * [PortalLiberatorConfig.experimental] to true.
 * 
 * @see PortalLiberatorConfig.experimental
 */
@Target(AnnotationTarget.CLASS)
annotation class Experimental
