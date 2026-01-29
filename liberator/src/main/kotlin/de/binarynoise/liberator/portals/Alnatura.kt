package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.getAction
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toParameterMap
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.FormElement

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID("Alnatura-Kunden-WLAN")
object Alnatura : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "cppm-auth.alnatura.de"
    }
    
    fun getForm(response: Response): FormElement {
        val html = response.parseHtml()
        return html.getElementsByTag("form").single() as FormElement
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = response.followRedirects(client)
        val form1 = getForm(response1)
        val action1 = form1.getAction() ?: error("no action1")
        val formParameters1 = form1.toParameterMap()
        
        val response2 = client.postForm(response1.requestUrl, action1, formParameters1).followRedirects(client)
        val form2 = getForm(response2)
        val action2 = form2.getAction() ?: error("no action2")
        val formParameters2 = form2.toParameterMap()
        
        val response3 = client.postForm(response2.requestUrl, action2, formParameters2).followRedirects(client)
        val form3 = getForm(response3)
        val action3 = form3.getAction() ?: error("no action3")
        val formParameters3 = form3.toParameterMap()
        
        client.postForm(response3.requestUrl, action3, formParameters3).checkSuccess()
    }
}
