package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object MyPowerspotDE : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, client: OkHttpClient): Boolean {
        return "login.mypowerspot.de" == locationUrl.host
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.get(locationUrl, "/landingpage/")
        val response2 = client.get(response1.requestUrl, null, mapOf("acceptTOC" to "1"))
        val location2 = response2.getLocation() ?: error("no location2")
        check(location2.endsWith("success"))
        response2.followRedirects(client).checkSuccess()
    }
}
