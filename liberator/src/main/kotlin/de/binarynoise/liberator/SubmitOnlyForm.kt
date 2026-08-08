@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator

import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * This special [PortalLiberator] is to be used if other liberators fail,
 * as a simple last-resort test if [submitOnlyForm] works.
 */
object SubmitOnlyForm : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        if (!response.isSuccessful) return false
        if (response.parseHtml().forms().size != 1) return false
        return true
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        response.submitOnlyForm(client)
    }
}
