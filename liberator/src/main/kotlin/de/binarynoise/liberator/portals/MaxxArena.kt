@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("MesseSpot")
object MaxxArena : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "hotspot.maxxarena.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.postForm(response.requestUrl, "/", mapOf("auth" to "free"))
    }
}
