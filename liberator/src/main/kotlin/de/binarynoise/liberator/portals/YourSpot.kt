@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submit
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("EDEKA free-wifi")
object YourSpot : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "captive.your-spot.de" && response.requestUrl.decodedPath == "/login"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val html = response.parseHtml()
        val sendInForm = html.expectForm("form[name=sendin]")
        sendInForm.submit(client, response.requestUrl).checkSuccess()
    }
}
