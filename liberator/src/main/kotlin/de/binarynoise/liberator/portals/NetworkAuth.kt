package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object NetworkAuth : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return locationUrl.host.endsWith("network-auth.com")
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val continue_url = locationUrl.queryParameter("continue_url") ?: error("no continue_url")
        
        client.get(
            locationUrl, "grant", queryParameters = mapOf(
                "continue_url" to continue_url
            )
        ).followRedirects(client).checkSuccess()
    }
}
