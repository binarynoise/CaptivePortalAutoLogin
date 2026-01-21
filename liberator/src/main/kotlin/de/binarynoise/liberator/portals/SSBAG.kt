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
@SSID("SSB fuer Dich - WiFi Free")
object SSBAG : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "gast11.ssb-ag.de" == response.requestUrl.host && !response.isRedirect
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = response.parseHtml()
        val token = html1.getInput("token")
        client.postForm(
            response.requestUrl,
            "AupSubmit.action?from=AUP",
            mapOf(
                "token" to token,
                "aupAccepted" to "true",
            ),
        ).checkSuccess()
    }
}
