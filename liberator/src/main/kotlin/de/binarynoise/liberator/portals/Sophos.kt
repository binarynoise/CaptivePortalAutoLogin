@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.isIp
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID(
    "Museum",
    "memox.guest",
)
object Sophos : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.isIp //
            && response.requestUrl.port == 4501 //
            && response.requestUrl.decodedPath == "/index.cgi"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        response.submitOnlyForm(client).checkSuccess()
    }
}
