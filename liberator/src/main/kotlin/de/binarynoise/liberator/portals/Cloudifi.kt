@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID(
    "Sephora Where Wifi Beats",
)
object Cloudifi : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "login.cloudi-fi.net"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val response1 = client.postForm(
            response.requestUrl,
            null,
            mapOf(
                "source" to "directregister",
                "username" to "null",
            ),
        )
        response1.submitOnlyForm(client).followRedirects(client)
    }
}
