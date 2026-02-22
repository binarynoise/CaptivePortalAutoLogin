package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.isIp
import de.binarynoise.util.okhttp.lastPathSegment
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID("Solarbank-WLAN", mustMatch = true)
object StadtwerkeStuttgart : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.isIp // 
            && response.requestUrl.lastPathSegment == "macauth" //
            && listOf("uamport", "uamip", "userurl").all { response.requestUrl.hasQueryParameter(it) }
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val uamip = response.requestUrl.queryParameter("uamip") ?: error("no uamip")
        val uamport = response.requestUrl.queryParameter("uamport")?.toInt() ?: error("no uamport")
        val uamaddr = HttpUrl.Builder().scheme("http").host(uamip).port(uamport).encodedPath("/logon").build()
        val userurl = response.requestUrl.queryParameter("userurl") ?: error("no userurl")
        client.get(
            uamaddr,
            null,
            mapOf(
                "username" to "",
                "password" to "",
                "userurl" to userurl,
                "agreetos" to "1",
            ),
        ).followRedirects(client).checkSuccess()
    }
}
