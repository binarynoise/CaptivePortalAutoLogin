package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.isIp
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID("Fotoprofi-Gast", mustMatch = true)
object FotoProfi : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.isIp //
            && response.requestUrl.pathSegments.last() == "index.shtml" // 
            && response.requestUrl.hasQueryParameter("redirect")
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val redirect = response.requestUrl.queryParameter("redirect") ?: error("no redirect")
        val html = response.parseHtml()
        client.postForm(
            response.requestUrl, "/wgcgi.cgi", mapOf(
                "action" to html.getInput("action"),
                "hsContinueBtnName" to html.getInput("hsContinueBtnName"),
                "redirect" to redirect,
                "hsAcceptCkbName" to "on",
                "lang" to "en-US",
                "style" to html.getInput("style"),
            )
        )
    }
}
