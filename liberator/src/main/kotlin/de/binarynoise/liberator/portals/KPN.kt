@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.getAction
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submit
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("KPN")
object KPN : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "login.wifi.kpn.com"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val form = response.parseHtml().forms().single()
        check(form.getAction() == "/Home/StartSessionForFree")
        form.submit(client, response.requestUrl)
    }
}
