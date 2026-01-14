package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postForm
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object Commerzbank : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "wifiaccess.co" == locationUrl.host && locationUrl.pathSegments.lastOrNull() == "portal"
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.postForm(locationUrl, "/portal_api.php", mapOf("action" to "init")).checkSuccess()
        client.postForm(
            locationUrl,
            "/portal_api.php",
            mapOf("action" to "subscribe", "type" to "one", "policy_accept" to "true"),
        ).checkSuccess()
    }
}
