package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("RRX Hotspot")
object IOB : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "portal.iob.de" == response.requestUrl.host
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.get(null, "http://192.168.44.1/prelogin").followRedirects(client)
        Hotsplots.solve(client, response1, cookies)
    }
}
