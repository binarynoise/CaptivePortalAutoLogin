package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.isIp
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID("SOLARBANK-WLAN", mustMatch = true)
object StadtwerkeStuttgart : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.isIp // 
            && response.requestUrl.firstPathSegment == "login" // 
            && response.requestUrl.hasQueryParameter("dst")
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val dst = response.requestUrl.queryParameter("dst") ?: error("no dst")
        client.postForm(
            response.requestUrl, null, mapOf(
                "dst" to dst,
                "popup" to "false",
                "username" to "anonymous",
                "password" to "anonymous",
            )
        ).checkSuccess()
    }
}
