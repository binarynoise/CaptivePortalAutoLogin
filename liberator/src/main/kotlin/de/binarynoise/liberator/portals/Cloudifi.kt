@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.randomEmail
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submit
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID(
    "Sephora Where Wifi Beats",
)
object Cloudifi : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "login.cloudi-fi.net" && !response.isRedirect
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val html = response.parseHtml()
        val form = html.expectForm("#declarative-authentication-form")
        val response1 = form.submit(
            client,
            response.requestUrl,
            mapOf(
                "username" to randomEmail(),
            ),
        )
        response1.submitOnlyForm(client).followRedirects(client)
    }
}
