package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("Nordsee Gast")
object Nordsee : PortalLiberator {
    fun HttpUrl.queryEntries(): Map<String, String> {
        return (0..this.querySize).associate {
            this.queryParameterName(it) to (this.queryParameterValue(it) ?: "")
        }
    }
    
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "guests.nordsee.com" == locationUrl.host
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = client.get(locationUrl, null).parseHtml()
        val pfsenseurl = html1.getInput("pfsenseurl").toHttpUrlOrNull() ?: error("failed to parse pfsenseurl")
        val queryEntries = pfsenseurl.queryEntries()
        
        client.postForm(
            pfsenseurl,
            null,
            queryEntries,
            queryEntries,
        ).followRedirects(client).checkSuccess()
    }
}
