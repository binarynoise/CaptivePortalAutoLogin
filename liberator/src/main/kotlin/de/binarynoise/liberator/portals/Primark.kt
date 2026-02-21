@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("PRIMARK_PUBLIC")
object Primark : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "www.primark.com"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.postForm(
            null,
            "https://portal.wifi.primark.net/auth/index.html/u",
            mapOf(
                "cmd" to "authenticate",
                "email" to "Customer@wifi.primark.net",
            ),
        )
    }
}
