@file:RequiresApi(Build.VERSION_CODES.Q)

package de.binarynoise.captiveportalautologin

import android.annotation.SuppressLint
import android.net.wifi.WifiConfiguration
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

fun getNetworkSuggestions(): List<WifiNetworkSuggestion> {
    log("getNetworkSuggestions: limit is ${wifiManager.maxNumberOfNetworkSuggestionsPerApp}")
    log("getNetworkSuggestions: current count is ${supportedSSIDSuggestions.size}")
    return supportedSSIDSuggestions.take(wifiManager.maxNumberOfNetworkSuggestionsPerApp)
}

fun resetNetworkSuggestions(): Boolean {
    val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        wifiManager.removeNetworkSuggestions(listOf(), WifiManager.ACTION_REMOVE_SUGGESTION_LINGER)
    } else {
        wifiManager.removeNetworkSuggestions(listOf())
    }
    log("removeNetworkSuggestions Status = ${status.toNetworkSuggestionStatusString()}")
    return status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
}

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

fun updateNetworkSuggestions(suggestions: List<WifiNetworkSuggestion> = getNetworkSuggestions()): Boolean {
    if (!SharedPreferences.network_suggestions.get()) return true
    return sendNetworkSuggestions(suggestions)
}

fun WifiNetworkSuggestion.getWifiConfiguration(): WifiConfiguration {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this::class.java.declaredMethods.single { it.name == "getWifiConfiguration" }.invoke(this) as WifiConfiguration
    } else {
        this::class.java.declaredFields.single { it.name == "wifiConfiguration" }.get(this) as WifiConfiguration
    }
}

fun WifiConfiguration.setMacRandomizationSettingCompat(macRandomizationSetting: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.macRandomizationSetting = macRandomizationSetting
    } else {
        this::class.java.declaredFields.single { it.name == "macRandomizationSetting" }
            .setInt(this, macRandomizationSetting)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
fun resetNetworkSuggestionMacAddress(ssid: String): Boolean {
    val suggestion = supportedSSIDSuggestions.single { it.ssid == ssid }
    return resetNetworkSuggestionMacAddress(suggestion)
}

fun resetNetworkSuggestionMacAddress(suggestion: WifiNetworkSuggestion): Boolean {
    return resetNetworkSuggestionMacAddress(listOf(suggestion))
}

@SuppressLint("InlinedApi")
fun resetNetworkSuggestionMacAddress(suggestion: List<WifiNetworkSuggestion>): Boolean {
    // removing a currently active NetworkSuggestion will disconnect from it immediately
    val removeStatus = wifiManager.removeNetworkSuggestions(suggestion)
    log("resetNetworkSuggestionMacAddress removeStatus=${removeStatus.toNetworkSuggestionStatusString()}")
    if (removeStatus != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS && removeStatus != WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID) return false
    suggestion.forEach { suggestion ->
        val wifiConfiguration = suggestion.getWifiConfiguration()
        wifiConfiguration.setMacRandomizationSettingCompat(WifiConfiguration.RANDOMIZATION_NON_PERSISTENT)
    }
    val addStatus = wifiManager.addNetworkSuggestions(suggestion)
    log("resetNetworkSuggestionMacAddress addStatus=${addStatus.toNetworkSuggestionStatusString()}")
    return addStatus == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
}
