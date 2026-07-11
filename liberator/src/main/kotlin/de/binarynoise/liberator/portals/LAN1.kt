@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID(
    "LAN1- Jugendgästehaus",
    "Novum Hotel Boulevard",
)
object LAN1 : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "login.lan1.de" && response.requestUrl.decodedPath == "/login"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val html = response.parseHtml()
        val mac = html.getInput("mac")
        client.postForm(response.requestUrl, null, mapOf("dst" to "", "username" to "T-$mac")).checkSuccess()
    }
}
