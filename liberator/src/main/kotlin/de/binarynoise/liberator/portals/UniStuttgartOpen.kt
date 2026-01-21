package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("uni-stuttgart-open")
object UniStuttgartOpen : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "guest-internet.tik.uni-stuttgart.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        response.checkSuccess()
        val html = response.parseHtml()
        val mac = html.getInput("mac")
        client.postForm(
            response.requestUrl,
            "/login",
            mapOf(
                "accept" to "1",
                "mac" to mac,
            ),
        ).checkSuccess()
    }
}
