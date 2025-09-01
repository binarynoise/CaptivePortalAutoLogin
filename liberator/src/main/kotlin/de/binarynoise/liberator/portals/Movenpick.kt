package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object Movenpick : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return "" == locationUrl.host
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        TODO("Not yet implemented")
    }
}
