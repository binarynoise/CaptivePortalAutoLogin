@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("HfMWiFi")
object ArubaInstantOn : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.decodedPath == "/swarm.cgi" //
            && response.requestUrl.queryParameter("opcode") == "cp_generate" // 
            && response.requestUrl.hasQueryParameter("orig_url")
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val orig_url = response.requestUrl.queryParameter("orig_url") ?: error("no orig_url")
        client.postForm(
            response.requestUrl.newBuilder().query(null).build(),
            null,
            mapOf(
                "opcode" to "cp_ack",
                "orig_url" to orig_url,
            ),
        ).checkSuccess()
    }
}
