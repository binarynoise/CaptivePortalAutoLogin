package de.binarynoise.captiveportalautologin

import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.portals.allPortalLiberators
import de.binarynoise.logger.Logger.log

val supportedSSIDs: List<String> = allPortalLiberators.flatMap { portalLiberator ->
    portalLiberator::class.java.annotations.filterIsInstance<SSID>().flatMap { it.ssid.asIterable() }
}

@RequiresApi(Build.VERSION_CODES.Q)
val supportedSSIDSuggestions = supportedSSIDs.map { ssid ->
    val builder = WifiNetworkSuggestion.Builder().setSsid(ssid).setIsMetered(false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setIsInitialAutojoinEnabled(true).setMacRandomizationSetting(
            if (SharedPreferences.network_suggestions_mac_randomization.get()) WifiNetworkSuggestion.RANDOMIZATION_NON_PERSISTENT
            else WifiNetworkSuggestion.RANDOMIZATION_PERSISTENT
        )
    }
    return@map builder.build()
}

val wifiManager by lazy { ContextCompat.getSystemService(applicationContext, WifiManager::class.java)!! }

@RequiresApi(Build.VERSION_CODES.Q)
fun getNetworkSuggestions(): List<WifiNetworkSuggestion> {
    log("getNetworkSuggestions: limit is ${wifiManager.maxNumberOfNetworkSuggestionsPerApp}")
    log("getNetworkSuggestions: current count is ${supportedSSIDSuggestions.size}")
    return supportedSSIDSuggestions.take(wifiManager.maxNumberOfNetworkSuggestionsPerApp)
}

@RequiresApi(Build.VERSION_CODES.Q)
fun resetNetworkSuggestions(): Boolean {
    val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        wifiManager.removeNetworkSuggestions(listOf(), WifiManager.ACTION_REMOVE_SUGGESTION_LINGER)
    } else {
        wifiManager.removeNetworkSuggestions(listOf())
    }
    log("removeNetworkSuggestions Status = ${status.toNetworkSuggestionStatusString()}")
    return status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
}

@RequiresApi(Build.VERSION_CODES.Q)
fun sendNetworkSuggestions(suggestions: List<WifiNetworkSuggestion> = getNetworkSuggestions()): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) resetNetworkSuggestions()
    val status = wifiManager.addNetworkSuggestions(suggestions)
    log("addNetworkSuggestions Status = ${status.toNetworkSuggestionStatusString()}")
    return status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
}

fun Number.toNetworkSuggestionStatusString(): String {
    return WifiManager::class.java.declaredFields.singleOrNull {
        it.name.startsWith("STATUS_NETWORK_SUGGESTIONS_") && it.get(null) == this
    }?.name?.removePrefix("STATUS_NETWORK_SUGGESTIONS_") ?: "UNKNOWN"
}

@RequiresApi(Build.VERSION_CODES.Q)
fun updateNetworkSuggestions(suggestions: List<WifiNetworkSuggestion> = getNetworkSuggestions()): Boolean {
    if (!SharedPreferences.network_suggestions.get()) return true
    return sendNetworkSuggestions(suggestions)
}
