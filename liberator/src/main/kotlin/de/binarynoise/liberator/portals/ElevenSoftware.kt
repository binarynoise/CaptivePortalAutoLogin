@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalRedirector
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.getLocationUrl
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("Aloft_Public")
object ElevenSoftware : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "secure.guestinternet.com" && response.requestUrl.hasQueryParameter("redirect")
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val redirectUrl = response.requestUrl.queryParameter("redirect")!!.toHttpUrl()
        val l_url = redirectUrl.queryParameter("L_URL")
        val processResponse = client.postForm(
            response.requestUrl,
            l_url,
            mapOf(
                "MARSHA" to "STRAL",
                "ACTION" to "CONNECTED",
                "SP" to "PLAN1",
            ),
        )
        if (!processResponse.isRedirect) error("processResponse is not a redirect")
        val processResponseLocation = processResponse.getLocationUrl()!!
        if (!processResponseLocation.hasQueryParameter("switch_url")) error("processResponse does not contain switch_url")
        val switchUrl = processResponseLocation.queryParameter("switch_url")!!
        client.postForm(
            null,
            switchUrl,
            mapOf(
                "username" to processResponseLocation.queryParameter("username"),
                "password" to processResponseLocation.queryParameter("password"),
                "dst" to processResponseLocation.queryParameter("dst"),
                "var" to processResponseLocation.queryParameter("var"),
            ),
        ).checkSuccess()
    }
}

@SSID("Aloft_Public")
object GatewayAuthRedirector : PortalRedirector {
    override fun canRedirect(response: Response): Boolean {
        return response.requestUrl.host.endsWith("gatewayauth.com") // 
            && response.requestUrl.decodedPath == "/login"
    }
    
    override fun redirect(client: OkHttpClient, response: Response, extras: LiberatorExtras): Response {
        return response.submitOnlyForm(client)
    }
}
