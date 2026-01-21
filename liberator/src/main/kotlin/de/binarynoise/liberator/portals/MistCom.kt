package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

// Rossmann
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object MistCom : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "portal.\\w+.mist.com".toRegex().matches(response.requestUrl.host)
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val ap_mac = response.requestUrl.queryParameter("ap_mac") ?: error("no ap_mac")
        val url = response.requestUrl.queryParameter("url") ?: error("no url")
        val client_mac = response.requestUrl.queryParameter("client_mac") ?: error("no client_mac")
        val wlan_id = response.requestUrl.queryParameter("wlan_id") ?: error("no wlan_id")
        
        client.postForm(
            response.requestUrl, "/logon", mapOf(
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
