@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

object FritzBox : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.decodedPath == "/untrusted_guest.lua"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        client.get(response.requestUrl, "/trustme.lua?accept=").checkSuccess()
    }
}

object FritzBoxBlocked : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "fritz.box" && response.requestUrl.decodedPath == "/blocked"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        throw UnsupportedPortalException("FRITZ!Box blocks internet access")
    }
}
