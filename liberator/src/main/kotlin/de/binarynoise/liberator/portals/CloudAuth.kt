@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "pap_guest",
)
object CloudAuth : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host.endsWith(".aio.cloudauth.net") //
            && response.requestUrl.decodedPath == "/swarm.cgi"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        response.submitOnlyForm(client)
    }
}
