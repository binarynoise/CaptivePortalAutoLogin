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

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object FritzBox : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "untrusted_guest.lua" == response.requestUrl.decodedPath
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        client.get(response.requestUrl, "/trustme.lua?accept=").checkSuccess()
    }
}

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object FritzBoxBlocked : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "fritz.box" == response.requestUrl.host && "blocked" == response.requestUrl.decodedPath
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        throw UnsupportedPortalException("FRITZ!Box blocks internet access")
    }
}
