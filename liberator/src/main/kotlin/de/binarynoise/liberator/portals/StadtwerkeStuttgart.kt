package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.isIp
import de.binarynoise.util.okhttp.lastPathSegment
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toParameterMap
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.FormElement

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID("Solarbank-WLAN", mustMatch = true)
object StadtwerkeStuttgart : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.isIp && response.requestUrl.lastPathSegment == "macauth"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val form = html.getElementsByTag("form").single() as FormElement
        val url = form.attr("action").takeIf { it.isNotEmpty() } ?: error("no form action url")
        client.get(response.requestUrl, url, form.toParameterMap()).checkSuccess()
    }
}
