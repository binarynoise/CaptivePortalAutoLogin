package de.binarynoise.liberator

import de.binarynoise.logger.Logger.log
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

object PortalLiberatorConfig {
    var experimental: Boolean = false
        set(value) {
            field = value
            log("PortalLiberatorConfig.debug = $value")
        }
}

interface UnIndexedPortalLiberator {
    /**
     * Determines if this portal liberator can handle the given captive portal location.
     * 
     * @param locationUrl The HTTP URL from the Location header of the captive portal redirect response.
     *                    This is typically the URL where the captive portal login page is hosted.
     * @return true if this liberator knows this captive portal and can solve it, false otherwise.
     */
    fun canSolve(locationUrl: HttpUrl, response: Response): Boolean
    
    /**
     * Attempts to solve the captive portal by performing the necessary authentication actions.
     * 
     * @param locationUrl The HTTP URL from the Location header that this liberator has identified as solvable.
     *                    This is the same URL passed to canSolve() and represents the portal entry point.
     * @param client The OkHttpClient instance configured to make HTTP requests to the portal.
     * @param response The HTTP response that contains the redirect to this captive portal.
     * @param cookies The current set of cookies collected during the portal detection process.
     */
    fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>)
}

interface PortalLiberator : UnIndexedPortalLiberator

@Target(AnnotationTarget.CLASS)
annotation class SSID(vararg val ssid: String)

@Target(AnnotationTarget.CLASS)
annotation class Verified
