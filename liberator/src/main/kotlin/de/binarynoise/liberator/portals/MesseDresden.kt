@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("Dresden")
object MesseDresden : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "aerolan.ibh.de" //
            && response.requestUrl.firstPathSegment == "messe"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val loginButton = html.getElementsByTag("a").single { it.attr("href").contains("login.html") }
        val loginUrl = loginButton.attr("href")
        check(loginUrl != "") { "loginUrl is empty" }
        client.get(response.requestUrl, loginUrl)
    }
}
