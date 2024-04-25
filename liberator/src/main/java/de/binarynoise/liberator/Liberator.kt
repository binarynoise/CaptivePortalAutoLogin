@file:Suppress("MemberVisibilityCanBePrivate") @file:OptIn(ExperimentalContracts::class)

package de.binarynoise.liberator

import java.net.SocketTimeoutException
import kotlin.contracts.ExperimentalContracts
import de.binarynoise.logger.Logger.log
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlin.IllegalStateException as ISE

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
    
    /**
     * Intercepts the request, adds User-Agent, Connection and Cookie headers,
     * logs request details, POST request body, proceeds with the request, logs response details,
     * saves cookies and logs the response body
     */
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
            val cookiesToSend = cookies.filter { it.matches(originalRequest.url) }
            log("Loading cookies for ${originalRequest.url}: ${cookiesToSend.joinToString { "${it.name}=${it.value}" }}")
            if (cookiesToSend.isNotEmpty()) {
                val cookieHeader = cookiesToSend.joinToString(separator = "; ") { "${it.name}=${it.value}" }
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
        var text = response.readText(skipStatusCheck = true)
        
        val newCookies = Cookie.parseAll(newRequest.url, response.headers)
        if (newCookies.isNotEmpty()) {
            log("Saving cookies for ${newRequest.url}: ${newCookies.joinToString { "${it.name}=${it.value}" }}")
            newCookies.forEach { new ->
                val old = cookies.find { old -> old.name == new.name }
                if (old != null) {
                    cookies -= old
                }
                cookies += new
            }
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
    
    /**
     * Attempts to liberate the user by making a series of HTTP requests to the portal.
     *
     * @return null if the user is not caught in the portal, the url of the portal otherwise
     *         Returns "Timeout" if a socket timeout exception occurs during the requests.
     */
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
            // Germany, Dortmund, bus, Westfalenhallen
            // verified
            // DSW21-WLAN, "Hotspot Westfalenhallen"
            //<editor-fold defaultstate="collapsed">
            //
            // https://controller.dokom21.de/?dst=...
            // https://hotspot.dokom21.de/bus~/?...
            // https://hotspot.dokom21.de/bus~/Index
            "hotspot.dokom21.de" == locationUrl.host && "/([^/]+)/Index".toRegex().matches(locationUrl.encodedPath) -> {
                val networkType = locationUrl.pathSegments.first() // bus~
                
                // https://hotspot.dokom21.de/bus~/Index | Cookie: ASP.NET_SessionId=...
                val response1 = client.get(null, locationUrl)
                
                val response1b = client.get("/$networkType/Login", response1.requestUrl) // https://hotspot.dokom21.de/bus~/Login/
                
                val html1 = response1b.parseHtml()
                
                val __EVENTTARGET = html1.selectFirst("input[name=__EVENTTARGET]")?.attr("value") ?: throw ISE("no __EVENTTARGET")
                val __EVENTARGUMENT = html1.selectFirst("input[name=__EVENTARGUMENT]")?.attr("value") ?: throw ISE("no __EVENTARGUMENT")
                val __LASTFOCUS = html1.selectFirst("input[name=__LASTFOCUS]")?.attr("value") ?: throw ISE("no __LASTFOCUS")
                val __VIEWSTATE = html1.selectFirst("input[name=__VIEWSTATE]")?.attr("value") ?: throw ISE("no __VIEWSTATE")
                val __VIEWSTATEGENERATOR = html1.selectFirst("input[name=__VIEWSTATEGENERATOR]")?.attr("value")
                    ?: throw ISE("no __VIEWSTATEGENERATOR")
                val __EVENTVALIDATION = html1.selectFirst("input[name=__EVENTVALIDATION]")?.attr("value") ?: throw ISE("no __EVENTVALIDATION")
                
                val response2 = client.post(
                    null,
                    response1b.requestUrl, // https://hotspot.dokom21.de/bus~/Login/
                    mapOf(
                        "__EVENTTARGET" to __EVENTTARGET, // leer
                        "__EVENTARGUMENT" to __EVENTARGUMENT, // leer
                        "__VIEWSTATE" to __VIEWSTATE,
                        "__LASTFOCUS" to __LASTFOCUS, // leer
                        "__VIEWSTATEGENERATOR" to __VIEWSTATEGENERATOR,
                        "__EVENTVALIDATION" to __EVENTVALIDATION,
                        
                        "ctl00\$GenericContent\$AgbAccepted" to "on",
                        "ctl00\$GenericContent\$PrivacyPolicyAccepted" to "on",
                        "ctl00\$LanguageSelect" to "DE",
                        "ctl00\$GenericContent\$SubmitLogin" to "Einloggen",
                    ),
                )
                // https://controller.dokom21.de/portal_api.php?action=authenticate&...
                val url2 = response2.getLocation() ?: throw ISE("no location 2")
                val response3 = client.get(url2, response2.requestUrl)
                val url3 = response3.getLocation() ?: throw ISE("no location 3") // https://hotspot.dokom21.de/bus~/?...
                val response4 = client.get(url3, response3.requestUrl)
                val url4 = response4.getLocation() ?: throw ISE("no location 4") // https://hotspot.dokom21.de/bus~/Login
                val response5 = client.get(url4, response4.requestUrl)
                response5.checkSuccess()
                check(response5.readText().contains("Login erfolgreich."))
            }
            //</editor-fold>
            
            // Germany, Deutsche Bahn
            //<editor-fold defaultstate="collapsed">
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
                    || "login.wifionice.de" == locationUrl.host -> { // http://login.wifionice.de/?url=...
                val response1 = client.get(null, locationUrl)
                val location1 = response1.getLocation() // https://login.wifionice.de/?url=...
                
                val response2 = client.get(location1, response1.requestUrl)
                val location2 = response2.getLocation() // https://login.wifionice.de/de/ | Cookie: csrf
                
                val response3 = client.get(location2, response2.requestUrl)
                
                val csrfToken = cookies.find { it.name == "csrf" }?.value ?: throw ISE("no csrf")
                client.post(
                    null, response3.requestUrl, mapOf(
                        "login" to "true",
                        "CSRFToken" to csrfToken,
                    )
                ).getLocation() // https://iceportal.de
            }
            
            // verified
            // WIFI@DB
            "www.hotsplots.de" == locationUrl.host && "/auth/login.php" == locationUrl.encodedPath -> {
                val response1 = client.get(location, response.requestUrl)
                val html1 = response1.parseHtml()
                
                val challenge = html1.selectFirst("input[name=challenge]")?.attr("value") ?: throw ISE("no challenge")
                val uamip = html1.selectFirst("input[name=uamip]")?.attr("value") ?: throw ISE("no uamip")
                val uamport = html1.selectFirst("input[name=uamport]")?.attr("value") ?: throw ISE("no uamport")
                val userurl = html1.selectFirst("input[name=userurl]")?.attr("value") ?: throw ISE("no userurl")
                val myLogin = html1.selectFirst("input[name=myLogin]")?.attr("value") ?: throw ISE("no myLogin")
                val ll = html1.selectFirst("input[name=ll]")?.attr("value") ?: throw ISE("no ll")
                val nasid = html1.selectFirst("input[name=nasid]")?.attr("value") ?: throw ISE("no nasid")
                val custom = html1.selectFirst("input[name=custom]")?.attr("value") ?: throw ISE("no custom")
                val haveTerms = html1.selectFirst("input[name=haveTerms]")?.attr("value") ?: throw ISE("no haveTerms")
                
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
            //</editor-fold>
            
            // Germany, Stuttgart, Messe
            //<editor-fold defaultstate="collapsed">
            /*    "https://wifi.berner-messe.de".isStartOf(location) -> { // no trailing '/'
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
            //</editor-fold>
            
            // Germany, Kaufland, Rewe
            // https://portal-eu-ffm01.conn4.com/ident?client_ip=...&client_mac=...&site_id=15772&signature=...&loggedin=0&remembered_mac=0
            //<editor-fold defaultstate="collapsed">
            (locationUrl.host.endsWith(".conn4.com") && locationUrl.firstPathSegment == "ident") -> {
                val site_id = locationUrl.queryParameter("site_id") ?: throw ISE("no site_id")
                val response1 = client.get(null, locationUrl)
                val location1 = response1.getLocation() // https://portal-eu-ffm01.conn4.com/#
                
                val response2 = client.get(location1, response1.requestUrl)
                val token = response2.readText().let { html ->
                    // find and parse
                    // conn4.hotspot.wbsToken = { "token": "...", "urls": { "grant_url": null, "continue_url": null } };
                    val wbsTokenIndex = html.indexOf("hotspot.wbsToken")
                    check(wbsTokenIndex != -1) { "hotspot.wbsToken not found" }
                    val jsObjectStart = html.indexOf("{", wbsTokenIndex)
                    check(jsObjectStart != -1) { "jsObjectStart not found" }
                    val jsObjectEnd = html.indexOf(";", jsObjectStart) - 1
                    check(jsObjectEnd != -1) { "jsObjectEnd not found" }
                    
                    val jsObject = JSONObject(html.substring(jsObjectStart, jsObjectEnd))
                    jsObject.getString("token")
                }
                
                val response3 = client.post(
                    "/wbs/api/v1/create-session/", locationUrl,
                    mapOf(
                        "authorization" to "token=$token",
                        "locale" to "de_DE",
                        "locationId" to site_id,
                        "session_id" to "",
                        "with-tariffs" to "1",
                    ),
                )
                val session = JSONObject(response3.readText()).getString("session") ?: throw ISE("no session")
                
                val response4 = client.post(
                    "/wbs/api/v1/register/free/", locationUrl,
                    mapOf(
                        "authorization" to "session:$session",
                        "registration_type" to "terms-only",
                        "registration[terms]" to "1",
                    ),
                )
                check(JSONObject(response4.readText()).getBoolean("ok"))
            }
            //</editor-fold>
            
            // IKEA
            // "IKEA WiFi"
            //<editor-fold defaultstate="collapsed">
            "yo-wifi.net" == locationUrl.host && "authen" == locationUrl.firstPathSegment -> {
                val response1 = client.get(location, response.requestUrl)
                
                // https://cp7-wifi.net/?device_name=ide223&user_mac=96:c0:a0:2d:11:c0&device_hostname=yo-wifi.net&ssid=/#/login
                val url1 = response1.getLocation() ?: throw ISE("no location 1")
                val mac = response1.requestUrl.queryParameter("user_mac") ?: throw ISE("no mac")
                val deviceName = response1.requestUrl.queryParameter("device_name") ?: throw ISE("no deviceName")
                
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
                val url4 = response4.getLocation() ?: throw ISE("no location 4") // https://cp7-wifi.net/success?
                val url5 = client.get(url4, response4.requestUrl).getLocation() ?: throw ISE("no location 5") // https://cp7-wifi.net/?...#/success
                client.get(url5, response4.requestUrl).checkSuccess()
            }
            //</editor-fold>
            
            // MediaMarkt / Saturn (?)
            // media-kunden
            //<editor-fold defaultstate="collapsed">
            "192.0.2.1" == locationUrl.host -> { // "http://192.0.2.1/fs/customwebauth/login.html?switch_url=http://192.0.2.1/login.html&ap_mac=00:a2:ee:a8:5c:a0&client_mac=2a:8a:49:3b:11:ce&wlan=media-kunden&redirect=am-i-captured.binarynoise.de/"
                val switch_url = locationUrl.queryParameter("switch_url") ?: throw ISE("no login_url")
                val redirect_url = locationUrl.queryParameter("redirect") ?: throw ISE("no redirect_url")
                client.post(
                    switch_url, locationUrl,
                    mapOf(
                        "del[]" to "on",
                        "redirect_url" to redirect_url,
                        "err_flag" to "0",
                        "buttonClicked" to "4",
                    ),
                ).checkSuccess()
            }
            // </editor-fold>
            
            // fritz.box guest wifi
            // verified
            //<editor-fold defaultstate="collapsed">
            "untrusted_guest.lua" == locationUrl.firstPathSegment -> {
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
            //</editor-fold>
            
            else -> {
                log("unknown captive portal: $location")
                // follow redirects and try again
                inner(client.get(location, response.requestUrl))
            }
        }
    }
}
