@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

/*
ArubaLP networks can be identified by the initial response being 200 OK,
then with a meta refresh it redirects to a url containing cmd=redirect&arubalp=12345
https://support.hpe.com/hpesc/public/docDisplay?docId=sf000095199en_us&docLocale=en_US
*/

@SSID("PRIMARK_PUBLIC")
object Primark : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "www.primark.com"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.postForm(
            null,
            "https://portal.wifi.primark.net/auth/index.html/u",
            mapOf(
                "cmd" to "authenticate",
                "email" to "Customer@wifi.primark.net",
            ),
        )
    }
}

@Experimental
@SSID("Segmueller-Hotspot")
object Segmueller : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "hotspot.segmueller.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.postForm(
            null,
            "https://captiveportal-login.segmueller.de/cgi-bin/login",
            mapOf(
                "cmd" to "authenticate",
                "url" to "http://www.segmueller.de",
                "user" to "SEG_Anonymous",
                "Login" to "Log In",
                "password" to "069424",
            ),
        )
    }
}
