package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object SocialwiBox : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "hotspot.socialwibox.com" == response.requestUrl.host
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = response.parseHtml()
        val form1 = html1.getElementsByTag("form").singleOrNull() ?: error("no form1")
        check(form1.attr("name") == "redirect") { "form name: ${form1.attr("name")} != redirect" }
        val location1 = form1.attr("action")
        val inputs1 = form1.getElementsByTag("input")
            .filterNot { it.attr("type") == "submit" }
            .associate { input -> input.attr("name") to input.attr("value") }
        
        val response2 = client.postForm(response.requestUrl, location1, inputs1)
        val html2 = response2.parseHtml()
        val location2 = html2.selectXpath("footer > span.links-foot > a").single().attr("href")
        
        val response3 = client.get(response.requestUrl, location2).followRedirects(client)
        val html3 = response3.parseHtml()
        
        val script3 = html3.getElementsByTag("script").find { it.wholeText().contains("redirectPost") }
            ?: error("no script with redirectPost")
        
        val regex = "redirectPost\\('([^']+)',\\s*(\\{.*\\})\\);".toRegex()
        val match = regex.find(script3.wholeText()) ?: error("no match for redirectPost regex")
        val url = match.groups[1]?.value ?: error("no url in match")
        val data = match.groups[2]?.value ?: error("no data in match")
        val json = JSONObject(data)
        
        val response4 = client.postForm(response2.requestUrl, url, json.toMap().mapValues { (_, v) -> v.toString() })
        val html4 = response4.parseHtml()
        val form4 = html4.getElementsByTag("form").singleOrNull() ?: error("no form4")
        val location4 = form4.attr("action")
        val inputs4 = form4.getElementsByTag("input")
            .filterNot { it.attr("type") == "submit" }
            .associate { input -> input.attr("name") to input.attr("value") }
        
        val response5 = client.postForm(response2.requestUrl, location4, inputs4)
        response5.checkSuccess()
    }
}
