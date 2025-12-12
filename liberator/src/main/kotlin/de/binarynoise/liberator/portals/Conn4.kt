package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalLiberatorConfig
import de.binarynoise.liberator.asIterable
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

// Kaufland, Rewe
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object Conn4 : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return PortalLiberatorConfig.experimental && locationUrl.host.endsWith(".conn4.com") && locationUrl.firstPathSegment == "ident"
    }
    
    @Suppress("KotlinUnreachableCode")
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
//        val (r: Response, l: String?) = if (locationUrl.pathSegments.isEmpty()) {
//            // https://wbs-rewe.conn4.com/
//            val response0 = client.get(locationUrl, null)
//            val location0 = response0.getLocation()
//            (response0 to location0)
//        } else {
//            (response to null)
//        }
        
        // https://1193.rdr.conn4.com/ident?client_ip=10.41.57.140&client_mac=2ADD70A19A17&site_id=1193&signature=...&loggedin=0&remembered_mac=0
        // https://13329.bundbhotels.conn4.com/ident?client_ip=10.71.2.40&client_mac=1A941A354ACE&site_id=13329&signature=...&loggedin=0&remembered_mac=0
        // https://15965.rdr.conn4.com/ident?client_ip=10.1.42.175&client_mac=B6459C927851&site_id=15965&signature=...&loggedin=0&remembered_mac=0
        // https://portal-eu-ffm01.conn4.com/ident?client_ip=10.94.33.164&client_mac=9E92618A86AF&site_id=8274&signature=...&loggedin=0&remembered_mac=0
        // https://rewe-wlan.conn4.com/ident?client_mac=5E0A09C52F79&client_ip=10.50.14.17&site_id=13900&signature=...
        // https://rewe-wlan.conn4.com/ident?client_mac=C6659C5AFC3C&client_ip=10.50.12.12&site_id=13900&signature=...
        val response1 = client.get(locationUrl, null)
        val site_id = locationUrl.queryParameter("site_id") ?: error("no site_id")
        
        val location1 = response1.getLocation()
        
        // https://1193.rdr.conn4.com/#
        // https://13329.bundbhotels.conn4.com/#
        // https://15965.rdr.conn4.com/#
        // https://portal-eu-ffm01.conn4.com/#
        // https://rewe-wlan.conn4.com/#
        val response2 = client.get(response1.requestUrl, location1)
        val html2 = response2.parseHtml()
        val scriptNode = html2.getElementsByTag("script").find { it.data().contains("conn4.hotspot.wbsToken") }
            ?: error("no script with conn4.hotspot.wbsToken")
        val assignments = RhinoParser().parseAssignments(scriptNode.data())
        val token = assignments["conn4.hotspot.wbsToken.token"] ?: error("no token")
        
        val schedule = JSONObject(assignments["_.partial.1"] ?: error("no _.partial.1")).getJSONObject("schedule")
            ?: error("no schedule")
        
        val sceneIds = schedule.getJSONArray("events").asIterable().map { event ->
            runCatching {
                event as JSONObject
                val payload = event.getJSONObject("payload") ?: error("no payload")
                if (payload.getString("type") != "scene") error("type != scene")
                val data = payload.getJSONObject("data")
                if (data.has("module") && data.getString("module").contains("offline")) error("offline")
                data.getString("id")
            }
        }.successes().getOrElse {
            throw IllegalStateException("all scenes failed:" + it.message, it)
        }.toSet()
        
        check(sceneIds.isNotEmpty()) { "no scenes" }
        
        val template = schedule.getString("scene_template") ?: error("no scene_template")
        
        val apiBase = sceneIds.asSequence().map { id ->
            runCatching {
                val responseScene = client.get(locationUrl, template.replace("{id}", id))
                val htmlScene = responseScene.parseHtml()
                
                val element =
                    htmlScene.getElementsByAttribute("data-wbs-base-url").getOrNull(0) ?: error("no data-wbs-base-url")
                val wbsBaseUrl =
                    element.attr("data-wbs-base-url").takeIf(String::isNotEmpty) ?: error("no data-wbs-base-url (blank")
                val wbsBaseUrlSlash = wbsBaseUrl.let { baseUrl -> if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/" }
                responseScene.requestUrl.resolve(wbsBaseUrlSlash) ?: error("no data-wbs-base-url (invalid url)")
            }
        }.firstSuccess().getOrElse {
            throw IllegalStateException("no scene with data-wbs-base-url :" + it.message, it)
        }
        
        
        // https://1193.rdr.conn4.com/wbs/api/v1/login/free/
        // https://13329.bundbhotels.conn4.com/wbs/api/v1/create-session/
        // https://15965.rdr.conn4.com/wbs/api/v1/create-session/
        // https://portal-eu-ffm01.conn4.com/wbs/api/v1/create-session/
        
        // https://wbs-rewe.conn4.com/api/v1/create-session/
        val response3 = client.postForm(
            apiBase, "create-session/",
            mapOf(
                "authorization" to "token=$token",
                "locale" to "de_DE",
                "locationId" to site_id,
                "session_id" to "",
                "with-tariffs" to "1",
            ),
        )
        val session3 = JSONObject(response3.readText()).getString("session") ?: error("no session")
        
        // https://1193.rdr.conn4.com/wbs/api/v1/login/free/
        // https://13329.bundbhotels.conn4.com/wbs/api/v1/login/free/
        // https://15965.rdr.conn4.com/wbs/api/v1/login/free/
        // https://portal-eu-ffm01.conn4.com/wbs/api/v1/login/free/
        // https://wbs-rewe.conn4.com/api/v1/login/free/
        // https://wbs-rewe.conn4.com/api/v1/login/free/
        
        val response4 = client.postForm(
            apiBase, "register/free/",
            mapOf(
                "authorization" to "session=$session3",
                "registration_type" to "terms-only",
                "registration[terms]" to "1",
            ),
        )
        check(JSONObject(response4.readText()).getBoolean("ok")) { "response4 not ok" }
        
        val response5 = client.postForm(
            apiBase, "create-session/",
            mapOf(
                "authorization" to "token=$token",
                "locale" to "de_DE",
                "locationId" to "",
                "session_id" to session3,
                "with-tariffs" to "1",
            ),
        )
        val json5 = JSONObject(response5.readText())
        check(json5.getBoolean("ok")) { "response5 not ok" }
//        val session5 = json5.getString("session")
//        
//        val response6 = client.postForm(
//            apiBase, "statistics/",
//            mapOf(
//                "authorization" to "session=$session5"
//            ),
//        )
//        check(JSONObject(response6.readText()).getBoolean("ok")) { "response6 not ok" }
    }
}

fun <T> Sequence<Result<T>>.firstSuccess(): Result<T> {
    val exceptions = mutableListOf<Throwable>()
    for (result in this) {
        result.onSuccess { return result }
        result.onFailure { exceptions.add(it) }
    }
    val exception = NoSuchElementException("no success: " + exceptions.joinToString(", ") { it.message.toString() })
    for (exception in exceptions) {
        exception.addSuppressed(exception)
    }
    return Result.failure(exception)
}

fun <T> List<Result<T>>.successes(): Result<List<T>> {
    val exceptions = mutableListOf<Throwable>()
    val successes = mutableListOf<T>()
    for (result in this) {
        result.onSuccess { successes.add(it) }
        result.onFailure { exceptions.add(it) }
    }
    if (successes.isNotEmpty()) {
        return Result.success(successes)
    }
    
    val exception = NoSuchElementException("no success: " + exceptions.joinToString(", ") { it.message.toString() })
    for (exception in exceptions) {
        exception.addSuppressed(exception)
    }
    return Result.failure(exception)
}
