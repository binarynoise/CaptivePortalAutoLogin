@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("NEU_Carglass-Gast-Zugang", mustMatch = true)
object Carglass : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.decodedPath == "/reg.php" && response.requestUrl.hasQueryParameter("url")
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        client.postForm(
            response.requestUrl.newBuilder().query("").build(),
            null,
            mapOf(
                "url" to (response.requestUrl.queryParameter("url") ?: error("no url")),
                "checkbox" to "checkbox",
            ),
        )
    }
}
