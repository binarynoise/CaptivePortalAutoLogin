package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("Hotspot S-Bahn Rhein-Ruhr")
object RheinRuhr : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return "10.10.10.1" == locationUrl.host && 2050 == locationUrl.port && "splash.html" == locationUrl.firstPathSegment
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = client.get(locationUrl, null).parseHtml()
        client.get(
            locationUrl, "/nodogsplash_auth/", queryParameters = mapOf(
                "tok" to html1.getInput("tok"),
                "redir" to html1.getInput("redir"),
            )
        ).checkSuccess()
    }
}
