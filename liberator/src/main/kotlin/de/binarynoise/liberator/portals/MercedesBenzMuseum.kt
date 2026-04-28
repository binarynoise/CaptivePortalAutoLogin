@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("MBMuseum_FreeWifi")
object MercedesBenzMuseum : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "wifi.media.mercedes-benz.museum"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        client.postForm(
            response.requestUrl,
            "/null",
            mapOf(
                "switch_url" to "",
                "redirect_url" to "success.html",
                "err_flag" to "",
                "buttonClicked" to "4",
            ),
        ).checkSuccess()
    }
}
