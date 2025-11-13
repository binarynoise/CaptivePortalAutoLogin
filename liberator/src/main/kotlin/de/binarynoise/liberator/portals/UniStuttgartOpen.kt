package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("uni-stuttgart-open")
object UniStuttgartOpen : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return locationUrl.host == "guest-internet.tik.uni-stuttgart.de"
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response = client.get(locationUrl, null)
        response.checkSuccess()
        val html = response.parseHtml()
        val mac = html.getInput("mac")
        client.postForm(
            locationUrl,
            "/login",
            mapOf(
                "accept" to "1",
                "mac" to mac,
            ),
        ).checkSuccess()
    }
}
 
