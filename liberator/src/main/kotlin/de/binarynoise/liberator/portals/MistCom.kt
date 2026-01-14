package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postForm
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

// Rossmann
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object MistCom : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "portal.\\w+.mist.com".toRegex().matches(locationUrl.host)
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val ap_mac = locationUrl.queryParameter("ap_mac") ?: error("no ap_mac")
        val url = locationUrl.queryParameter("url") ?: error("no url")
        val client_mac = locationUrl.queryParameter("client_mac") ?: error("no client_mac")
        val wlan_id = locationUrl.queryParameter("wlan_id") ?: error("no wlan_id")
        
        client.postForm(
            locationUrl, "/logon", mapOf(
                "ap_mac" to ap_mac,
                "auth_method" to "passphrase",
                "tos" to "true",
                "url" to url,
                "client_mac" to client_mac,
                "wlan_id" to wlan_id,
            )
        ).checkSuccess()
    }
}
