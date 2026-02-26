package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID("Alnatura-Kunden-WLAN")
object Alnatura : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "cppm-auth.alnatura.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = response.followRedirects(client)
        val response2 = response1.submitOnlyForm(client).followRedirects(client)
        val response3 = response2.submitOnlyForm(client).followRedirects(client)
        response3.submitOnlyForm(client).checkSuccess()
    }
}
