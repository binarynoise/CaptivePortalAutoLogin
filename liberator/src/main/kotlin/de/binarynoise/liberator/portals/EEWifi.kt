@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submit
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("EE WiFi")
object EEWifi : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "ee-wifi.ee.co.uk" && response.requestUrl.firstPathSegment == "home"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        response.parseHtml().expectForm("loginForm-eeb").submit(client, response.requestUrl).checkSuccess()
    }
}
