
package de.binarynoise.liberator.portals

import java.util.*
import kotlin.time.Clock
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalLiberatorConfig
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

// Spain Airports
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("AIRPORT FREE WIFI AENA")
object AenaES : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return PortalLiberatorConfig.experimental && "freewifi.aena.es" == locationUrl.host
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val freeWifiBase = "https://freewifi.aena.es/".toHttpUrl()
        val loginBase = "https://login.aena.es/".toHttpUrl()

//        val response1 = client.get(locationUrl, null)
//        val location1 = response1.getLocation() ?: error("no location1")
        // https://freewifi.aena.es/473fad84-1203-4d8d-8abf-6abb463db3ef?cmd=login&mac=_&ip=_&essid=%20&apname=_&apgroup=&url=_
        val location1 = locationUrl.toString()
        
        val response2 = client.get(locationUrl, null) // get cookies
        val uuid = response2.requestUrl.firstPathSegment
        val gigyaApiKey: String = response2.readText().let { text ->
            val pattern = "gigyaApiKey\\s*=\\s*[\"']([^\"']+)[\"']'".toRegex()
            pattern.find(text)?.groupValues?.get(1) ?: error("no gigyaApiKey")
        }
        val mac = response2.requestUrl.queryParameter("mac") ?: error("no mac")
        
        // https://freewifi.aena.es/api/portal/473fad84-1203-4d8d-8abf-6abb463db3ef

//        val response3 = client.get(freeWifiBase, "/api/portal/$uuid")
//        val json3 = JSONObject(response3.readText())
        //  {"portal":{"id":"473fad84-1203-4d8d-8abf-6abb463db3ef","title":"Valencia","name":"VLC-Valencia","organizationId":"68c55545-5f90-445b-bd0d-69508bab91a4","provider":"custom","locales":["en"],"mode":null,"placeId":"4a2325e9-ee9d-4e32-a90c-4c4611fcfbec"},"status":200}

//        val response4 = client.postJson(
//            freeWifiBase, "/api/portal/$uuid/events", JSONObject(
//                mapOf(
//                    "metric" to "main_counter",
//                )
//            ).toString()
//        )
//        val json4 = JSONObject(response4.readText())
        // {"user":{"id":"473fad84-1203-4d8d-8abf-6abb463db3ef","type":"portal","attributes":{"name":"VLC-Valencia","title":"Valencia","force_recover_data":false,"check_schedule":false,"status":true,"is_debug_mode":false,"autologin":true,"days_autologin":0,"allowed_domains":"","secret":null,"provider_session_duration":28800,"provider_session_idle_duration":28800,"provider_download_bandwidth":5000,"provider_upload_bandwidth":5000,"max_devices":0,"coa_verification":true,"coa_verification_time":15,"provider_meraki_domain":null,"country_code":null,"access_url":"https://cwp.fl4m3.com/473fad84-1203-4d8d-8abf-6abb463db3ef","access_url_landing":"https://cwp.fl4m3.com/473fad84-1203-4d8d-8abf-6abb463db3ef/ok","is_raw_content":false,"radius_active":true,"limited_access_active":false,"render_std_file":null,"limited_access_duration":300,"limited_access_download_bandwidth":"","limited_access_upload_bandwidth":"","limited_access_landing_url":null,"limited_access_user":"admin","limited_access_password":"pass","http_external_validation_command":null,"actual_language":"es","primary_language":"spanish","welcome_email":null,"mode":null,"locales":["en"],"connection_modes":[],"provider_options":{"redirect_url":"www.google.es","session_duration":"28800","upload_bandwidth":"5000","download_bandwidth":"5000","session_idle_duration":"28800"},"external_portal_access":false,"provider":"custom","provider_redirect_url":"www.google.es","organization_id":"68c55545-5f90-445b-bd0d-69508bab91a4","ad_campaigns":[],"redirects":[],"market_uuids":["4a2325e9-ee9d-4e32-a90c-4c4611fcfbec"],"places":[{"id":"4a2325e9-ee9d-4e32-a90c-4c4611fcfbec","name":"VLC-Valencia"}]}},"statusCode":200}
        
        
        var loginID: String
        var tries = 0
        do {
            loginID = when (tries++) {
                in 0..5 -> {
                    UUID.randomUUID().toString() + "@aena.es"
                }
                in 6..10 -> {
                    UUID.randomUUID().toString() + "@" + UUID.randomUUID().toString().substringBefore("-") + ".com"
                }
                else -> {
                    error("could not generate valid email")
                }
            }
            
            val response6 = client.postForm(
                null, "https://login.aena.es/accounts.isAvailableLoginID", mapOf(
                    "format" to "json",
                    "pageUrl" to location1,
                    "sdk" to "js_latest",
                    "sdkBuild" to "0",
                    "loginID" to loginID,
                    "authMode" to "cookie",
                    "APIKey" to gigyaApiKey,
                )
            )
            val json6 = JSONObject(response6.readText())
            // {
            //  "callId": "b6928c0a9d7645178dce65aa19110e38",
            //  "errorCode": 0,
            //  "apiVersion": 2,
            //  "statusCode": 200,
            //  "statusReason": "OK",
            //  "time": "2025-08-20T18:38:35.608Z",
            //  "isAvailable": true
            //}
            Thread.sleep(1000)
        } while (json6.getBoolean("isAvailable"))
        
        
        val response5 = client.get(
            null, "https://login.aena.es/accounts.initRegistration",
            mapOf(
                "format" to "json",
                "isLite" to "true",
                "APIKey" to gigyaApiKey,
                "skd" to "latest",
                "sdkBuild" to "0",
                "authMode" to "cookie",
                "pageUrl" to location1,
            ),
        )
        val json5 = JSONObject(response5.readText())
        // {
        //  "callId": "08046be0408c449e8cf270eb606826be",
        //  "errorCode": 0,
        //  "apiVersion": 2,
        //  "statusCode": 200,
        //  "statusReason": "OK",
        //  "time": "2025-08-20T18:39:01.895Z",
        //  "regToken": "tk2.LTE.AtLtamgxwA.KnLMa-lbIrkqvlv34UjtWy2zDwJa-nArJ3jabbbeMA4.pKUdS_s3MkZ91zPHpY0A42M0OxikwlPGB0o13oGClqubFwx9n3cFK7wbQOV3ARKZ5GZ29kiEHKG-XlPUxs-wlw.sc3"
        // }
        val regToken = json5.getString("regToken")
        
        
        //format=json
        //source=showScreenSet
        //pageURL=https://freewifi.aena.es/473fad84-1203-4d8d-8abf-6abb463db3ef?cmd=login&mac=96:37:51:3c:4b:15&ip=172.33.56.89&essid=+&apname=0/0/2&apgroup=&url=http://am-i-captured.binarynoise.de/
        //regToken=tk2.LTE.AtLtamgxwA.KnLMa-lbIrkqvlv34UjtWy2zDwJa-nArJ3jabbbeMA4.pKUdS_s3MkZ91zPHpY0A42M0OxikwlPGB0o13oGClqubFwx9n3cFK7wbQOV3ARKZ5GZ29kiEHKG-XlPUxs-wlw.sc3
        //profile={"email":"blodsinnig@aena.com"}
        //sdk=js_latest
        //data={"service":{"WIFI":"true"},"inicioRelacion":{"WIFI":"2025-08-20T18:38:57.664Z"},"userType":"LITE","isLiteVerified":"false"}
        //lang=de
        //sdkBuild=17783
        //displayedPreferences={"terms_Wifi":{"docVersion":null,"docDate":"2024-05-09T00:00:00Z"},"privacy_Aena":{"docVersion":null,"docDate":"2025-03-11T00:00:00Z"},"communications_Encuestas":{"docVersion":null,"docDate":"2025-04-09T00:00:00Z"}}
        //APIKey=4_0acIUmA92tsS4wBLcnoWCw
        //preferences={"terms_Wifi":{"isConsentGranted":true},"privacy_Aena":{"isConsentGranted":"true"},"communications_Encuestas":{"isConsentGranted":"true"}}
        
        
        val response7 = client.postForm(
            loginBase, "/accounts.setAccountInfo", mapOf(
                "format" to "json",
                "source" to "showScreenSet",
                "pageURL" to location1,
                "regToken" to regToken,
                "profile" to JSONObject(
                    mapOf<String, Any>(
                        "email" to loginID,
                    )
                ).toString(),
                "sdk" to "js_latest",
                "sdkBuild" to "0",
                "lang" to "de",
                "APIKey" to gigyaApiKey,
                "data" to JSONObject(
                    mapOf(
                        "service" to mapOf("WIFI" to "true"),
                        "inicioRelacion" to mapOf("WIFI" to Clock.System.now().toString()),
                        "userType" to "LITE",
                        "isLiteVerified" to "false",
                    )
                ).toString(),
                "displayedPreferences" to JSONObject(
                    mapOf<String, Any>(
                        "terms_Wifi" to mapOf("docVersion" to null, "docDate" to "2024-05-09T00:00:00Z"),
                        "privacy_Aena" to mapOf("docVersion" to null, "docDate" to "2025-03-11T00:00:00Z"),
                        "communications_Encuestas" to mapOf("docVersion" to null, "docDate" to "2025-04-09T00:00:00Z"),
                    )
                ).toString(),
                "preferences" to JSONObject(
                    mapOf<String, Any>(
                        "terms_Wifi" to mapOf("isConsentGranted" to true),
                        "privacy_Aena" to mapOf("isConsentGranted" to true),
                        "communications_Encuestas" to mapOf("isConsentGranted" to true),
                    )
                ).toString(),
            )
        )
        val json7 = JSONObject(response7.readText())
        //{
        //  "callId": "50d38264d52243109eab6d9b533a4157",
        //  "errorCode": 0,
        //  "apiVersion": 2,
        //  "statusCode": 200,
        //  "statusReason": "OK",
        //  "time": "2025-08-20T18:39:03.022Z",
        //  "UID": "620e45fee38641278704889e32b9e190"
        //}
        check(json7.getInt("statusCode") == 200)
        
        val response8 = client.postJson(
            freeWifiBase, "/api/portal/$uuid/verifyAccount", JSONObject(
                mapOf<String, Any>(
                    "email" to loginID,
                )
            ).toString()
        )
        val json8 = JSONObject(response8.readText())
        check(json8.getInt("statusCode") == 200)
        
        
        // {"email":"blodsinnig@aena.com","user_id":"620e45fee38641278704889e32b9e190","social_network":"guest","language":"de","client_mac":"96:37:51:3c:4b:15","is_verified":false}
        
        val response9 = client.postJson(
            freeWifiBase, "/api/portal/$uuid/register", JSONObject(
                mapOf<String, Any>(
                    "email" to loginID,
                    "user_id" to json7.getString("UID"),
                    "social_network" to "guest",
                    "language" to "de",
                    "client_mac" to mac,
                    "is_verified" to false,
                )
            ).toString()
        )
        val json9 = JSONObject(response9.readText())
        // {"user":{"id":"aab94298-b617-4d6c-be41-73b42f59dfcb","type":"guest_wifi_user","attributes":{"userid":"620e45fee38641278704889e32b9e190","data_origin":"guest","device_mac":"96:37:51:3c:4b:15","location_name":"VLC-Valencia","accessed_days":0,"email":"blodsinnig@aena.com","name":null,"first_name":null,"last_name":"","gender":null,"phone":null,"last_seens":[],"birthday":null,"age":null,"country":null,"country_iso":null,"visit_times":1,"social_network":"guest","zip_code":null,"personal_interest":null,"promotional_code":null,"locale":"de","browser_language":null,"permit_service":"true","permit_communication":"true","permit_custom_checks":null,"finalities_json":{"COMMERCIAL":"true","SERVICE":"true"},"created":"2025-08-20T18:39:11Z","last_seen":"2025-08-20T18:39:11.097Z","last_seens_count":0,"accessed_at_count":1,"captive_portal_name":"VLC-Valencia","network_credentials":{"username":"394_9637513c4b15","password":"5XV4bqvYEEvmbiS8m3o6"}}},"statusCode":200}
        check(json9.getInt("statusCode") == 200)
        
        
        // {"device_mac":"96:37:51:3c:4b:15","role_name":"guest","is_verified":false,"is_emergency":false}
        val response10 = client.postJson(
            freeWifiBase, "/api/network/authorize", JSONObject(
                mapOf<String, Any>(
                    "device_mac" to mac,
                    "role_name" to "guest",
                    "is_verified" to false,
                    "is_emergency" to false,
                )
            ).toString()
        )
        val json10 = JSONObject(response10.readText())
        check(json10.getInt("statusCode") == 200)
    }
}
