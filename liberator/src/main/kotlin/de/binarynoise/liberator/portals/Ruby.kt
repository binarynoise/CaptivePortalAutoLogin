@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import org.jsoup.nodes.Document

@SSID(
    "RUBY-HOTEL",
    "Ruby Workspaces",
)
object Ruby : PortalLiberator {
    
    fun computeCHAPHash(string: String, chapId: List<Char>, chapChallenge: List<Char>): String {
        val chapString = chapId.joinToString("") + string + chapChallenge.joinToString("")
        val buffer = Buffer()
        buffer.write(chapString.toByteArray(Charsets.ISO_8859_1))
        val md5 = buffer.md5()
        return md5.toByteArray().joinToString("") { it.toHexString() }
    }
    
    fun parseCHAPParametersFromHTML(html: Document): Pair<List<Char>, List<Char>> {
        val chapScript = html.getElementsByTag("script").single { it.data().contains("hexMD5(") }.data()
        val hexmd5 = chapScript.substringAfter("hexMD5(", "").substringBefore(")", "")
        check(hexmd5.isNotEmpty()) { "hexmd5 is empty" }
        fun String.unquote(): String {
            return this.substringAfter("'").substringBeforeLast("'")
        }
        
        fun octalChar(codePoint: Int): Char {
            return codePoint.toString().toInt(8).toChar()
        }
        
        fun String.parseRubyChars(): List<Char> {
            return this.split("\\").filterNot { it.isEmpty() }.map { it.toInt() }.map { octalChar(it) }
        }
        
        val chapId = hexmd5.substringBefore("+").trim().unquote().parseRubyChars()
        check(chapId.isNotEmpty()) { "chapId is empty" }
        val chapChallenge = hexmd5.substringAfterLast("+").trim().unquote().parseRubyChars()
        check(chapChallenge.isNotEmpty()) { "chapChallenge is empty" }
        return Pair(chapId, chapChallenge)
    }
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "hotspot.ruby-workspaces.com" // 
            && response.requestUrl.encodedPath == "/login" //
            && response.requestUrl.hasQueryParameter("dst")
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val html = response.parseHtml()
        val form = html.getElementsByTag("form").single { it.attr("name") == "login" }
        val action = form.attr("action").takeIf { it.isNotEmpty() }?.toHttpUrl(response.requestUrl)
            ?: response.requestUrl.newBuilder().query(null).build()
        val chapParameters = parseCHAPParametersFromHTML(html)
        client.postForm(
            action, null, mapOf(
                "username" to form.getInput("username"),
                "dst" to form.getInput("dst"),
                "popup" to "true",
                "password" to computeCHAPHash(form.getInput("password"), chapParameters.first, chapParameters.second)
            )
        )
    }
}
