package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object FritzBox : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "untrusted_guest.lua" == locationUrl.firstPathSegment
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.get(locationUrl, "/trustme.lua?accept=").checkSuccess()
    }
}
