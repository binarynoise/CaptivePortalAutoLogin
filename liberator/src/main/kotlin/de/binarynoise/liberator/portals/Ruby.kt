@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toHttpUrlOrNull
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("RUBY-HOTEL")
object RubyHotels : PortalLiberator {
    fun isRubyHotelsLoginUrl(url: HttpUrl): Boolean {
        return url.host == "hotspot.ruby-hotels.com" //
            && url.encodedPath == "/login" //
            && url.hasQueryParameter("dst")
    }
    
    override fun canSolve(response: Response): Boolean {
        return isRubyHotelsLoginUrl(response.requestUrl)
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val loginUrl = html.getElementsByTag("a") //
            .filter { it.hasAttr("href") }
            .map { it.attr("href") }
            .mapNotNull { it.toHttpUrlOrNull(response.requestUrl) }
            .single { isRubyHotelsLoginUrl(it) && it.hasQueryParameter("username") }
        client.get(loginUrl, null).checkSuccess()
    }
}
