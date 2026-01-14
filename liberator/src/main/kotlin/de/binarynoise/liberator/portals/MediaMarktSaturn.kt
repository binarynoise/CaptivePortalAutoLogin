package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.postForm
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("media-kunden")
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object MediaMarktSaturn : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "192.0.2.1" == locationUrl.host && locationUrl.decodedPath == "fs/customwebauth/login.html"
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val switch_url = locationUrl.queryParameter("switch_url") ?: error("no login_url")
        val redirect_url = locationUrl.queryParameter("redirect") ?: error("no redirect_url")
        client.postForm(
            locationUrl, switch_url,
            mapOf(
                "del[]" to "on",
                "redirect_url" to redirect_url,
                "err_flag" to "0",
                "buttonClicked" to "4",
            ),
        ).checkSuccess()
    }
}
