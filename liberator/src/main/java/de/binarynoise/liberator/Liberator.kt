@file:Suppress("MemberVisibilityCanBePrivate")

package de.binarynoise.liberator

import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.charset.Charset
import de.binarynoise.logger.Logger.log
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jetbrains.annotations.Contract
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

const val portalTestUrl = "http://am-i-captured.binarynoise.de" // TODO move to preference

class Liberator(private val clientInit: OkHttpClient.Builder.() -> Unit) {
    
    private val cookies: MutableSet<Cookie> = mutableSetOf()
    
    private var client = OkHttpClient.Builder().apply {
        cache(null)
        retryOnConnectionFailure(true)
        followRedirects(false)
        followSslRedirects(true)
        
        addInterceptor(::interceptRequest)
        
        clientInit()
    }.build()
    
    private fun interceptRequest(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder().apply {
            header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            header(
                "Connection",
                "Keep-Alive",
            )
            
            log("Loading cookies for ${originalRequest.url}: ${cookies.joinToString { "${it.name}=${it.value}" }}")
            val cookies = cookies.filter { it.matches(originalRequest.url) }
            if (cookies.isNotEmpty()) {
                val cookieHeader = cookies.joinToString(separator = "; ") { "${it.name}=${it.value}" }
                header("Cookie", cookieHeader)
            }
        }.build()
        
        log("> ${newRequest.method} ${newRequest.url}")
        newRequest.headers.forEach { (name, value) ->
            log("> $name: $value")
        }
        if (newRequest.method == "POST") {
            when (val body = newRequest.body) {
                is FormBody -> for (i in 0..<body.size) {
                    val name = body.name(i)
                    val value = body.value(i)
                    log("> $name=$value")
                }
                null -> {
                }
                else -> log("> ${body.contentType()} (${body.contentLength()} bytes)")
            }
        }
        
        val response = chain.proceed(newRequest)
        
        log("< ${response.code} ${response.message}")
        response.headers.forEach { (name, value) ->
            log("< $name: $value")
        }
        var text = response.readText()
        
        val newCookies = Cookie.parseAll(newRequest.url, response.headers)
        if (newCookies.isNotEmpty()) {
            log("Saving cookies for ${newRequest.url}: ${newCookies.joinToString { "${it.name}=${it.value}" }}")
            cookies += newCookies
            log("All cookies now: ${cookies.joinToString { "${it.name}=${it.value}" }}")
        }
        
        // prettify text if html, xml or json
        val contentType = response.header("Content-Type")
        if (contentType != null) when {
            contentType.startsWith("text/html") -> text = Jsoup.parse(text).html()
            contentType.startsWith("text/xml") -> text = Jsoup.parse(text).body().html()
            contentType.startsWith("application/json") -> text = JSONObject(text).toString(2)
        }
        
        log(text)
        
        return response
    }
    
    fun liberate(): String? {
        try {
            val response = client.get(portalTestUrl, null)
            if (response.getLocation().isNullOrBlank()) {
                log("not caught in portal")
                return null
            }
            
            inner(response)
            
            Thread.sleep(1000)
            
            return client.get(portalTestUrl, null).getLocation()
        } catch (_: SocketTimeoutException) {
            return "Timeout"
        }
    }
    
    @Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName")
    private tailrec fun inner(response: Response) {
        val location = response.getLocation()
        if (location.isNullOrBlank()) return
        
        val locationUrl: HttpUrl = response.requestUrl.resolveOrThrow(location)
        
        when {
            // Germany, Dortmund, bus
            "hotspot.dokom21.de" == locationUrl.host && "/([^/]+)/Index".toRegex().matches(locationUrl.encodedPath) -> {
                val networkType = "/([^/]+)/Index".toRegex().matchEntire(locationUrl.path)!!.groupValues[1]
                
                val response1 = client.get(locationUrl.toString(), null)
                val html1 = response1.parseHtml()
                
                val __EVENTTARGET = html1.selectFirst("input[name=__EVENTTARGET]")!!.attr("value")
                val __EVENTARGUMENT = html1.selectFirst("input[name=__EVENTARGUMENT]")!!.attr("value")
                val __LASTFOCUS = html1.selectFirst("input[name=__LASTFOCUS]")!!.attr("value")
                val __VIEWSTATE = html1.selectFirst("input[name=__VIEWSTATE]")!!.attr("value")
                val __VIEWSTATEGENERATOR = html1.selectFirst("input[name=__VIEWSTATEGENERATOR]")!!.attr("value")
                val __EVENTVALIDATION = html1.selectFirst("input[name=__EVENTVALIDATION]")!!.attr("value")
                
                val response2 = client.post(
                    "https://controller.dokom21.de/$networkType/Login", null, mapOf(
                        "__EVENTTARGET" to __EVENTTARGET, // leer
                        "__EVENTARGUMENT" to __EVENTARGUMENT, // leer
                        "__VIEWSTATE" to __VIEWSTATE,
                        "__LASTFOCUS" to __LASTFOCUS, // leer
                        "__VIEWSTATEGENERATOR" to __VIEWSTATEGENERATOR,
                        "__EVENTVALIDATION" to __EVENTVALIDATION,
                        
                        "ctl00\$GenericContent\$AgbAccepted" to "on",
                        "ctl00\$GenericContent\$PrivacyPolicyAccepted" to "on",
                        "ctl00\$LanguageSelect" to "DE",
                        "ctl00\$GenericContent\$SubmitLogin" to "Einloggen"
                    )
                )
                val url2 = response2.getLocation()!!
                val response3 = client.post(url2, response2.requestUrl)
                val url3 = response3.getLocation()!! // https://controller.dokom21.de/portal_api.php?action=authenticate&...
                val response4 = client.post(url3, response3.requestUrl)
                val url4 = response4.getLocation()!! // https://hotspot.dokom21.de/bus~/?...
                val response5 = client.post(url4, response4.requestUrl)
                val url5 = response5.getLocation()!! // https://hotspot.dokom21.de/bus~/Login
                client.get(url5, response5.requestUrl).checkSuccess()
            }
            
            // Germany, Deutsche Bahn
            "portal.wifi.bahn.de" == locationUrl.host //
                    || "wifi.bahn.de" == locationUrl.host -> {
                client.post(
                    "/login", locationUrl, mapOf(
                        "login" to "oneclick",
                        "oneSubscriptionForm_connect_policy_accept" to "on",
                    )
                ).checkSuccess()
            }
            
            "wifi-bahn.de" == locationUrl.host //
                    || "login.wifionice.de" == locationUrl.host -> {
                var response1 = client.get(null, locationUrl)
                val location1 = response1.getLocation()
                if (location1 != null) {
                    response1 = client.get(location1, response1.requestUrl)
                }
                val html = response1.parseHtml()
                val csrfToken = html.selectFirst("input[name=CSRFToken]")!!.attr("value")
                client.post(
                    "http://wifi-bahn.de/", null, mapOf(
                        "login" to "true",
                        "CSRFToken" to csrfToken,
                    )
                ).checkSuccess()
            }
            
            // verified
            "192.168.44.1" == locationUrl.host && "/prelogin" == locationUrl.path -> {
                val response1 = client.get(location, null)
                val url1 = response1.getLocation()!! // https://www.hotsplots.de/auth/login.php?...
                
                val response2 = client.get(url1, response1.requestUrl)
                val html2 = response2.parseHtml()
                
                val challenge = html2.selectFirst("input[name=challenge]")!!.attr("value")
                val uamip = html2.selectFirst("input[name=uamip]")!!.attr("value")
                val uamport = html2.selectFirst("input[name=uamport]")!!.attr("value")
                val userurl = html2.selectFirst("input[name=userurl]")!!.attr("value")
                val myLogin = html2.selectFirst("input[name=myLogin]")!!.attr("value")
                val ll = html2.selectFirst("input[name=ll]")!!.attr("value")
                val nasid = html2.selectFirst("input[name=nasid]")!!.attr("value")
                val custom = html2.selectFirst("input[name=custom]")!!.attr("value")
                val haveTerms = html2.selectFirst("input[name=haveTerms]")!!.attr("value")
                
                val response3 = client.post(
                    "https://www.hotsplots.de/auth/login.php", null, mapOf(
                        "termsOK" to "on",
                        "termsChkbx" to "on",
                        "challenge" to challenge,
                        "uamip" to uamip,
                        "uamport" to uamport,
                        "userurl" to userurl,
                        "myLogin" to myLogin,
                        "ll" to ll,
                        "nasid" to nasid,
                        "custom" to custom,
                        "haveTerms" to haveTerms
                    )
                )
                
                val url3 = response3.getLocation()!! // http://192.168.44.1:80/logon?...
                
                val response4 = client.get(url3, response3.requestUrl)
                val url4 = response4.getLocation()!! // https://www.hotsplots.de/logon.php?res=success&...
                val response5 = client.get(url4, response4.requestUrl)
                response5.checkSuccess()
            }
            
            "www.hotsplots.de" == locationUrl.host && "/auth/login.php" == locationUrl.path -> {
                val response1 = client.get(location, response.requestUrl)
                val html1 = response1.parseHtml()
                
                val challenge = html1.selectFirst("input[name=challenge]")!!.attr("value")
                val uamip = html1.selectFirst("input[name=uamip]")!!.attr("value")
                val uamport = html1.selectFirst("input[name=uamport]")!!.attr("value")
                val userurl = html1.selectFirst("input[name=userurl]")!!.attr("value")
                val myLogin = html1.selectFirst("input[name=myLogin]")!!.attr("value")
                val ll = html1.selectFirst("input[name=ll]")!!.attr("value")
                val nasid = html1.selectFirst("input[name=nasid]")!!.attr("value")
                val custom = html1.selectFirst("input[name=custom]")!!.attr("value")
                val haveTerms = html1.selectFirst("input[name=haveTerms]")!!.attr("value")
                
                val response3 = client.post(
                    "https://www.hotsplots.de/auth/login.php", null, mapOf(
                        "termsOK" to "on",
                        "termsChkbx" to "on",
                        "challenge" to challenge,
                        "uamip" to uamip,
                        "uamport" to uamport,
                        "userurl" to userurl,
                        "myLogin" to myLogin,
                        "ll" to ll,
                        "nasid" to nasid,
                        "custom" to custom,
                        "haveTerms" to haveTerms
                    )
                )
                
                var responseW: Response = response3
                var locationW: String? = response3.getLocation()
                
                while (locationW != null) {
                    responseW = client.get(locationW, responseW.requestUrl)
                    locationW = responseW.getLocation()
                }
                
                responseW.checkSuccess()
            }
            
            /*// Germany, Stuttgart, Messe
                "https://wifi.berner-messe.de".isStartOf(location) -> { // no trailing '/'
                    val chromeDesktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36" // Chrome Desktop
                    
                    val json1 = network.post(
                        "https://wifi.berner-messe.de/portal_api.php",
                        mapOf(
                            "action" to "subscribe",
                            "type" to "one",
                            "connect_policy_accept" to "true",
                        ),
                    ) {
                        setRequestProperty("User-Agent", chromeDesktopUserAgent)
                    }.readText()
                    
                    val subscribe = JSONObject(json1).getJSONObject("info").getJSONObject("subscribe")
                    val login = subscribe.getString("login")
                    val password = subscribe.getString("password")
                    
                    network.post(
                        "https://wifi.berner-messe.de/portal_api.php",
                        mapOf(
                            "action" to "authenticate",
                            "login" to login,
                            "password" to password,
                            "policy_accept" to "true",
                            "from_ajax" to "true",
                        ),
                    ) {
                        setRequestProperty("User-Agent", chromeDesktopUserAgent)
                    }.checkSuccess()
                }*/
            
            // Germany, Kaufland, Rewe
            "(https?://[\\w-]+.conn4.com)/ident.*".toRegex().matches(location) -> {
                val token = client.get("/", locationUrl).readText().let { html ->
                    val wbsTokenIndex = html.indexOf("hotspot.wbsToken").takeIf { it != -1 } ?: return
                    val jsObjectStart = html.indexOf("{", wbsTokenIndex) + 1
                    val jsObjectEnd = html.indexOf("}", jsObjectStart)
                    
                    val jsObject = JSONObject(html.substring(jsObjectStart, jsObjectEnd))
                    jsObject.getString("token")
                    
                }
                
                val session = client.post(
                    "/wbs/api/v1/create-session/", locationUrl, mapOf(
                        "authorization" to "token=$token",
                        "with-tariffs" to "1",
                    )
                ).readText().let { json ->
                    JSONObject(json).getString("session")
                }
                
                client.post(
                    "/wbs/api/v1/register/free/", locationUrl, mapOf(
                        "authorization" to "session:$session",
                        "registration_type" to "terms-only",
                        "registration[terms]" to "1",
                    )
                ).checkSuccess()
            }
            
            // IKEA
            "yo-wifi.net" == locationUrl.host && "/authen/" == locationUrl.path -> {
                val response1 = client.get(location, response.requestUrl)
                val url1 = response1.getLocation()!! // https://cp7-wifi.net/?device_name=ide223&user_mac=96:c0:a0:2d:11:c0&device_hostname=yo-wifi.net&ssid=/#/login
                val mac = response1.requestUrl.queryParameter("user_mac")!!
                val deviceName = response1.requestUrl.queryParameter("device_name")!!
                
                val response2 = client.get(url1, response1.requestUrl)
                // html with js form
                
                val response3 = client.post(
                    "/login/tc",
                    response2.requestUrl,
                    queryParameters = mapOf(
                        "client_id" to "1",
                        "nasid" to deviceName,
                        "save_mac" to "false",
                        "user_mac" to mac.replace(":", "-"),
                        "user_type" to "9", // TERMS_AND_CONDITIONS
                    ),
                )
                
                val json = JSONObject(response3.readText())
                
                val payload = json.getJSONObject("payload")
                val realm = payload.getString("realm")
                val username = payload.getString("username")
                val password = payload.getString("password")
                
                val userid = realm + "1\\" + username + "\\9"
                
                val response4 = client.get(
                    "/authen/login/",
                    response3.requestUrl,
                    mapOf(
                        "userid" to userid,
                        "password" to password,
                    ),
                )
                val url4 = response4.getLocation()!! // https://cp7-wifi.net/success?
                val url5 = client.get(url4, response4.requestUrl).getLocation()!! // https://cp7-wifi.net/?...#/success
                client.get(url5, response4.requestUrl).checkSuccess()
            }
            
            // fritz.box guest wifi
            // verified
            "/untrusted_guest.lua" == locationUrl.path -> {
                val base = location.toHttpUrl()
                client.get("/trustme.lua?accept=", base).checkSuccess()
                
                Thread.sleep(100)
                
                if (true) { // TODO
                    client.get(portalTestUrl, null).readText()
                    client.get(portalTestUrl, null).parseHtml()
                    client.post("https://am-i-captured.binarynoise.de/portal/index.html", null, mapOf("random" to "1")).checkSuccess()
                    client.get("https://am-i-captured.binarynoise.de/portal/index.html", null, mapOf("random" to "1")).checkSuccess()
                }
            }
            
            else -> {
                log("unknown captive portal: $location")
                // follow redirects and try again
                inner(client.get(location, response.requestUrl))
            }
        }
    }
}

@Contract("null, null, _ -> fail; null, null-> fail")
fun OkHttpClient.get(
    url: String?,
    context: HttpUrl?,
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    val request = Request.Builder().apply {
        val urlBuilder = if (url == null) {
            context?.newBuilder() ?: throw IllegalArgumentException("url and context cannot both be null")
        } else {
            if (context == null) {
                url.toHttpUrl().newBuilder()
            } else {
                context.newBuilder(url) ?: throw IllegalArgumentException("constructed not well-formed url: $this -> $url")
            }
        }
        
        queryParameters.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        url(urlBuilder.build())
        
        preConnectSetup()
    }.build()
    
    return newCall(request).execute()
}

fun OkHttpClient.post(
    url: String,
    context: HttpUrl?,
    content: Map<String, String> = emptyMap(),
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    val request = Request.Builder().apply {
        val urlBuilder = context?.newBuilder(url) ?: url.toHttpUrl().newBuilder()
        queryParameters.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        url(urlBuilder.build())
        
        val formBodyBuilder = FormBody.Builder()
        content.forEach { (key, value) ->
            formBodyBuilder.add(key, value)
        }
        post(formBodyBuilder.build())
        
        preConnectSetup()
    }.build()
    
    return newCall(request).execute()
}

fun Response.checkSuccess() {
    check(code in 200..399) {
        "HTTP error: $code $message"
    }
    val location = header("Location") // don't use this.getLocation() to prevent infinite recursion
    if (location != null) {
        val path = request.url.resolveOrThrow(location).path
        val pathContains40x = arrayOf("401", "403").any { path.contains(it) }
        check(!pathContains40x) {
            "Redirect to 401 or 403: $path"
        }
    }
}

fun Response.getLocation(): String? {
    checkSuccess()
    val header = header("Location")
    if (header != null) return header
    
    // parse html for redirect
    val html = parseHtml()
    val meta = html.selectFirst("""meta[http-equiv="refresh"]""")
    if (meta != null) {
        return meta.attr("content").substringAfter(';').substringAfter('=').trim()
    }
    return null
}

fun Response.parseHtml(): Document {
    checkSuccess()
    return Jsoup.parse(readText(), request.url.toString())
}

fun Response.readText(): String {
    checkSuccess()
    val charset: Charset = body!!.contentType()?.charset() ?: Charsets.UTF_8
    val source = body?.source() ?: return ""
    source.request(Long.MAX_VALUE)
    return source.buffer.clone().readString(charset)
}

val Response.requestUrl: HttpUrl
    get() = this.request.url

val HttpUrl.path: String
    get() = URLDecoder.decode(encodedPath, "UTF-8")

fun HttpUrl.resolveOrThrow(newPath: String): HttpUrl =
    newBuilder(newPath)?.build() ?: throw IllegalArgumentException("constructed not well-formed url: $this -> $newPath")

//fun Response.getCookies(): List<HttpCookie> = headers("Set-Cookie").filter { it.isNotBlank() }.flatMap { HttpCookie.parse(it) }
