@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.call
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("Breuninger-WiFi")
object BreuningerLand : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "welcome.eb-guest.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        client.call(response.requestUrl, "/").checkSuccess()
    }
}
