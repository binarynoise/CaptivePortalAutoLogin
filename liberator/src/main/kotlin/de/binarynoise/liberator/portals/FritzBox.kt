package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

object FritzBox : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, client: OkHttpClient): Boolean {
        return "untrusted_guest.lua" == locationUrl.firstPathSegment
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val base = response.followRedirects(client).requestUrl
        client.get(base, "/trustme.lua?accept=").checkSuccess()
    }
}
