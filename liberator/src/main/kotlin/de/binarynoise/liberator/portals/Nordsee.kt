package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object Nordsee : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "guests.nordsee.com" == locationUrl.host
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = client.get(locationUrl, null).parseHtml()
        val pfsenseurl = html1.getInput("pfsenseurl")
        
        val response2 = client.postForm(null, pfsenseurl, mapOf())
        response2.checkSuccess()
    }
}
