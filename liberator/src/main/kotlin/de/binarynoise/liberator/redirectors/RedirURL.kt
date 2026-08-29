package de.binarynoise.liberator.redirectors

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalRedirector
import de.binarynoise.liberator.SSID
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "3SonsGuest",
    "Block-House-WiFi",
    "NEU_Carglass-Gast-Zugang",
)
object RedirURL : PortalRedirector {
    fun getRedirURL(response: Response): String {
        val html = response.parseHtml()
        val script = html.getElementsByTag("script").single().data()
        val assignments = RhinoParser().parseAssignments(script)
        val redirURL = assignments["redirURL"] ?: error("no redirURL")
        return redirURL
    }
    
    override fun canRedirect(response: Response): Boolean {
        if (response.isRedirect) return false
        try {
            getRedirURL(response)
            return true
        } catch (_: Exception) {
            return false
        }
    }
    
    override fun redirect(
        client: OkHttpClient,
        response: Response,
        extras: LiberatorExtras,
    ): Response {
        val redirURL = getRedirURL(response)
        return client.get(response.requestUrl, redirURL)
    }
}
