package de.binarynoise.liberator

import de.binarynoise.logger.Logger.log
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

object PortalLiberatorConfig {
    var debug: Boolean = false
        set(value) {
            field = value
            log("PortalLiberatorConfig.debug = $value")
        }
}

interface UnIndexedPortalLiberator {
    fun canSolve(locationUrl: HttpUrl): Boolean
    fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>)
}

interface PortalLiberator : UnIndexedPortalLiberator

@Target(AnnotationTarget.CLASS)
annotation class SSID(vararg val ssid: String)

@Target(AnnotationTarget.CLASS)
annotation class Verified
