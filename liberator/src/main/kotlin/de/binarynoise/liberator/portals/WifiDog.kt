@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.isIp
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID(
    "ZEDO_Guest",
)
object WifiDog : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.isIp //
            && response.requestUrl.port == 2060  //
            && response.requestUrl.decodedPath == "/wifidog"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        //TODO: implement a check for auth_type = "none"
        val res = client.get(response.requestUrl, "/auth").parseJsonObject()
        check(res.getString("loginstatus") == "success") {
            throw IllegalStateException("loginstatus is not success: ${res.getString("loginstatus")}")
        }
    }
}
