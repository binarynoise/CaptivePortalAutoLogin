@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("INTERSPORTkundenwlan")
object Intersport : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "wlan.intersport-gruppe.de" && !response.isRedirect
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val loginUrl = html.getElementsByTag("a").single { it.attr("href").contains("/login") }.attr("href")
        client.get(response.requestUrl, loginUrl).checkSuccess()
    }
}
