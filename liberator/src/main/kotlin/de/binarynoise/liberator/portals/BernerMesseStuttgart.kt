package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@SSID("Messe-for free")
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object BernerMesseStuttgart : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return "wifi.berner-messe.de" == locationUrl.host
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        // https://wifi.berner-messe.de/?dst=...
        // -> https://wifi.berner-messe.de/195/portal/
        val response1 = response.followRedirects(client)
        
        
        val response2 = client.postForm(
            response1.requestUrl, "/portal_api.php",
            mapOf(
                "action" to "init",
                "free_urls[]" to "http://www.messe-stuttgart.de/wlan",
            ),
        ).checkSuccess()
        
        // {"user":{"nas_constructor":null,"isAuthenticatedThroughShibboleth":false,"lang":"de","incomingNetwork":{"value":"WLAN_Free"},"incomingNetworkID":{"value":"in1043"},"incomingZone":{"value":"Free"},"visitor_id":"a971829db7fd60ea","force_feedback_disconnect":false,"passwordDigest":{"value":"fake_value_kept_for_backward_compatibility"}},"portalEntryUrl":"\/?from_api=true","logon":{"policy":{"display":true,"mandatory":true,"displayLang":["de","en"],"defaultLang":"de","de":{"text":"Die Nutzungsbedingungen WLAN Nutzung habe ich gelesen und akzeptiert.","resume":null,"link":"\/portals\/_policies\/AGB_Free_de_me_170724_WLAN_Besucher_Nutzungsbedingungen_kostenlos_LMS_DE.pdf"},"en":{"text":"The Terms of WLAN usage I have read and accepted.","resume":null,"link":"\/portals\/_policies\/AGB_Free_en_me_170724_WLAN_Besucher_Nutzungsbedingungen_kostenlos_LMS_EN.pdf"}},"twitter":{"need_follow":false},"facebook":{"need_like":false}},"subscribe":{"count":1,"one":{"countField":0}},"displayRegistrationFirst":false,"restrictWelcomeTitleDisplay":false,"restrictWelcomeSubtitleDisplay":false,"private_policy":{"display":false},"customTextCollection":[],"auth_modes":{"one":true},"feedback":{"pms":{"customer":{"display":true},"message":{"display":true}},"caution":{"display":true},"requestedURL":{"display":true},"login":{"display":true},"profile":{"display":true},"services":{"display":true},"ipAddress":{"display":false},"incomingNetwork":{"display":false},"incomingZone":{"display":true},"multidevice":{"display":true},"validity":{"display":true},"forceDiscTimer":{"display":true},"totalTimeCredit":{"display":true},"totalConsumedTimeCredit":{"display":true},"timeCredit":{"display":true},"consumedData":{"display":true}},"ucopia_id":"6y3QDDsSgrCZBXiJMY3YOA==","pwdRecovery":false,"accountRefill":false,"lang":{"displayLang":["de","en"],"defaultLang":"de","customFieldsDefaultLang":null},"refreshInterval":50000,"step":"SUBSCRIBE","type":"ONE","sponsorship":{"enable":false,"status":null},"modifyPwd":true,"forceModifyPwd":true}
//        val json2 = JSONObject(response2.readText())
        
        
        val response3 = client.postForm(
            response1.requestUrl, "/portal_api.php",
            mapOf(
                "action" to "subscribe",
                "connect_policy_accept" to "true",
                "gender" to "",
                "prefix" to "",
                "email_address" to "",
                "type" to "on",
                "phone" to "",
                "interests" to "",
                "user_password" to "",
                "user_password_confirm" to "",
                "policy_accept" to "true",
                "user_login" to "",
            ),
        )
        
        // {"info":{"code":"info_one-subscribe_success","subscribe":{"login":"1827vgl","password":"rnssgpncgc","user_object":{}}},"user":{"passwordDigest":{"value":"fake_value_kept_for_backward_compatibility"}}}
        val json3 = JSONObject(response3.readText())
        
        val subscibe = json3.getJSONObject("info").getJSONObject("subscribe")
        val login = subscibe.getString("login")
        val password = subscibe.getString("password")
        
        val response4 = client.postForm(
            response1.requestUrl, "/portal_api.php",
            mapOf(
                "action" to "authenticate",
                "from_ajax" to "true",
                "login" to login,
                "password" to password,
                "policy_accept" to "true",
            ),
        )
        
        // {"authenticate_step":"SUBSCRIBE","authenticate_type":"ONE","step":"FEEDBACK","type":"CONNECT","user":{"login":{"value":"1827vgl"},"passwordDigest":{"value":"fake_value_kept_for_backward_compatibility"},"ipAddress":{"value":"10.47.114.186"},"profile":{"id":25,"value":"Messe-for-Free"},"services":{"value":"Full_Access"},"autoDisconnect":{"value":true},"schedule":{"value":[{"begin":{"day":"monday","hour":"00","min":"00"},"end":{"day":"sunday","hour":"24","min":"00"}}]},"validity":{"value":"1763679540"},"initTimeGMT":{"value":"1763564535"},"macAddress":{"value":"96:f2:62:8c:bc:2f"},"consumedData":{"download":{"value":0},"upload":{"value":0},"timestamp":{"value":1763564535},"renewTimestamp":{"value":1763618535},"timezone":{"value":{"isControllerTimezone":true,"zoneTimezone":"Europe\/Berlin"}},"extra":{"value":[{"available":{"download":0,"upload":0},"total":{"download":0,"upload":0},"isSumQuota":false,"bandwidth":{"download":6000,"upload":6000},"isDisconnectQuota":false},{"available":{"download":null,"upload":157286400},"total":{"download":null,"upload":157286400},"isSumQuota":true,"bandwidth":{"download":-1,"upload":-1},"isDisconnectQuota":true}]}},"incomingNetwork":{"value":"WLAN_Free"},"incomingNetworkID":{"value":"in1043"},"incomingZone":{"value":"Free"},"incomingVlan":{"value":"WLAN_Free"},"creationMode":{"value":"portal-one"},"universalTime":{"value":1763564535},"timezoneOffset":{"value":"3600"},"requestedURL":{"value":"http:\/\/connectivitycheck.gstatic.com\/generate_204"},"allowModPwdBySelf":false,"managePersonalSettings":false,"manageAccount":false},"informativeWidget":{"login":{"value":"1827vgl"},"profile":{"value":"Messe-for-Free"},"schedule":{"value":"L00-24*M00-24*R00-24*J00-24*V00-24*S00-24*D00-24"},"validity":{"endDate":{"value":"1763679540"}},"connectionTime":{"value":"1763564535"},"universalTime":{"value":1763564535},"timezoneOffset":{"value":"3600"}},"accountRefill":false}
        val json4 = JSONObject(response4.readText())
        
        check(json4.getJSONObject("user").getJSONObject("service").getString("value") == "Full_Access") {
            "service is not Full_Access"
        }
    }
}
