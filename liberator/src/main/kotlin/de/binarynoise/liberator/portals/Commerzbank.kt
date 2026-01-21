package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object Commerzbank : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "wifiaccess.co" == response.requestUrl.host && response.requestUrl.pathSegments.lastOrNull() == "portal"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.postForm(response.requestUrl, "/portal_api.php", mapOf("action" to "init")).checkSuccess()
        client.postForm(
            response.requestUrl,
            "/portal_api.php",
            mapOf("action" to "subscribe", "type" to "one", "policy_accept" to "true"),
        ).checkSuccess()
    }
}
