@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toHttpUrl
import de.binarynoise.util.okhttp.toHttpUrlOrNull
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer

fun isRubyLoginUrl(url: HttpUrl): Boolean {
    return url.encodedPath == "/login" //
        && url.hasQueryParameter("dst")
}

@Experimental
@SSID("RUBY-HOTEL")
object RubyHotels : PortalLiberator {
    fun isRubyHotelsLoginUrl(url: HttpUrl): Boolean {
        return url.host == "hotspot.ruby-hotels.com" && isRubyLoginUrl(url)
    }
    
    override fun canSolve(response: Response): Boolean {
        return isRubyHotelsLoginUrl(response.requestUrl)
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val loginUrl = html.getElementsByTag("a") //
            .filter { it.hasAttr("href") }
            .map { it.attr("href") }
            .mapNotNull { it.toHttpUrlOrNull(response.requestUrl) }
            .single { isRubyHotelsLoginUrl(it) && it.hasQueryParameter("username") }
        client.get(loginUrl, null).checkSuccess()
    }
}

@Experimental
@SSID("Ruby Workspaces")
object RubyWorkspaces : PortalLiberator {
    
    fun computeCHAPHash(string: String): String {
        fun octalChar(codePoint: Int): Char {
            return codePoint.toString().toInt(8).toChar()
        }
        
        val chapString = octalChar(233) + string + listOf(
            254, 13, 21, 57, 104, 150, 76, 200, 253, 31, 13, 212, 336, 257, 260, 161
        ).map { octalChar(it) }.joinToString("")
        val buffer = Buffer()
        buffer.write(chapString.toByteArray(Charsets.ISO_8859_1))
        val md5 = buffer.md5()
        return md5.toByteArray().joinToString("") { it.toHexString() }
    }
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "hotspot.ruby-workspaces.com" && isRubyLoginUrl(response.requestUrl)
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val form = html.getElementsByTag("form").single { it.attr("name") == "login" }
        val action = form.attr("action").takeIf { it.isNotEmpty() }?.toHttpUrl(response.requestUrl)
            ?: response.requestUrl.newBuilder().query(null).build()
        client.postForm(
            action, null, mapOf(
                "username" to form.getInput("username"),
                "dst" to form.getInput("dst"),
                "popup" to "true",
                "password" to computeCHAPHash(form.getInput("password"))
            )
        )
    }
}
