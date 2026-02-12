package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalRedirector
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("RRX Hotspot")
object IOB : PortalRedirector {
    override fun canRedirect(response: Response): Boolean {
        return response.requestUrl.host == "portal.iob.de"
    }
    
    override fun redirect(client: OkHttpClient, response: Response, cookies: Set<Cookie>): Response {
        return client.get(null, "http://192.168.44.1/prelogin")
    }
}
