package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID(
    "L’Osteria",
    "Henri Willig GUEST",
)
object UniFi : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.port == 8843 && response.requestUrl.encodedPath.startsWith("/guest/s/")
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        //TODO: check if tos only is enabled?
        client.postForm(response.requestUrl, "login", mapOf()).checkSuccess()
        //TODO: check if final redirect chain is necessary
    }
}
