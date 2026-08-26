@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.randomEmail
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submit
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "Sephora Where Wifi Beats",
)
object Cloudifi : PortalLiberator {
    const val CLOUDIFI_DOMAIN = "login.cloudi-fi.net"
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == CLOUDIFI_DOMAIN && !response.isRedirect
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
        
        var encounteredSuccess = false
        response1.submitOnlyForm(client).followRedirects(client) { url ->
            // redirection chain: firewall -> cloudifi -> redirected site
            // first wait for firewall redirection, then only follow redirects on cloudifi
            // prevents the final redirected site from loading
            if (url.host == CLOUDIFI_DOMAIN && url.decodedPath == "/success.php") encounteredSuccess = true
            if (!encounteredSuccess) return@followRedirects true
            return@followRedirects url.host == CLOUDIFI_DOMAIN
        }
    }
}
