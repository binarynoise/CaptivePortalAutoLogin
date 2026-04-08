@file:RequiresApi(Build.VERSION_CODES.Q)

package de.binarynoise.captiveportalautologin

import java.lang.reflect.Field
import android.annotation.SuppressLint
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.isExperimental
import de.binarynoise.liberator.portals.allPortalLiberators
import de.binarynoise.liberator.tryOrDefault
import de.binarynoise.logger.Logger.log
import org.lsposed.hiddenapibypass.HiddenApiBypass

val supportedSSIDs: List<String> =
    allPortalLiberators.filter { !it.isExperimental() || BuildConfig.DEBUG }.flatMap { portalLiberator ->
            portalLiberator::class.java.annotations.filterIsInstance<SSID>().flatMap { it.ssid.asIterable() }
        }

@SuppressLint("InlinedApi")
val supportedSSIDSuggestions = supportedSSIDs.map { ssid ->
    val builder = WifiNetworkSuggestion.Builder().setSsid(ssid).setIsMetered(false)
    val macRandomizationSetting =
        if (SharedPreferences.network_suggestions_mac_randomization.get() || isMacRandomizationForceEnabled) WifiNetworkSuggestion.RANDOMIZATION_NON_PERSISTENT
        else WifiNetworkSuggestion.RANDOMIZATION_PERSISTENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setIsInitialAutojoinEnabled(true).setMacRandomizationSetting(macRandomizationSetting)
    }
    val suggestion = builder.build()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        suggestion.setMacRandomizationSetting(macRandomizationSetting)
    }
    return@map suggestion
}

val wifiManager by lazy { ContextCompat.getSystemService(applicationContext, WifiManager::class.java)!! }

val isMacRandomizationSupported by lazy { tryOrDefault(true) { wifiManager.invokeHiddenMethod("isConnectedMacRandomizationSupported") as Boolean } }

const val SETTINGS_NON_PERSISTENT_MAC_RANDOMIZATION_FORCE_ENABLED_KEY = "non_persistent_mac_randomization_force_enabled"
val isMacRandomizationForceEnabled
    get() = Settings.Global.getInt(
        applicationContext.contentResolver,
        SETTINGS_NON_PERSISTENT_MAC_RANDOMIZATION_FORCE_ENABLED_KEY,
        0,
    ) == 1


fun getNetworkSuggestions(): List<WifiNetworkSuggestion> {
    log("getNetworkSuggestions: limit is ${wifiManager.maxNumberOfNetworkSuggestionsPerApp}")
    log("getNetworkSuggestions: current count is ${supportedSSIDSuggestions.size}")
    return supportedSSIDSuggestions.take(wifiManager.maxNumberOfNetworkSuggestionsPerApp)
}


fun removeNetworkSuggestions(
    suggestions: List<WifiNetworkSuggestion> = listOf(),
    action: Int = WifiManager.ACTION_REMOVE_SUGGESTION_LINGER,
): Boolean {
    val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        wifiManager.removeNetworkSuggestions(suggestions, action)
    } else {
        wifiManager.removeNetworkSuggestions(suggestions)
    }
    log("removeNetworkSuggestions Status = ${status.toNetworkSuggestionStatusString()}")
    return status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
}

fun sendNetworkSuggestions(suggestions: List<WifiNetworkSuggestion> = getNetworkSuggestions()): Boolean {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) removeNetworkSuggestions()
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        removeNetworkSuggestions(wifiManager.networkSuggestions - suggestions)
    }
    return sendNetworkSuggestions(suggestions)
}

fun Any.getHiddenInstanceField(name: String): Field {
    return HiddenApiBypass.getInstanceFields(this::class.java).single { it.name == name }
}

fun Any.invokeHiddenMethod(name: String, vararg args: Any?): Any {
    return HiddenApiBypass.invoke(this::class.java, this, name, *args)
}

fun WifiNetworkSuggestion.getWifiConfiguration(): WifiConfiguration {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.invokeHiddenMethod("getWifiConfiguration") as WifiConfiguration
    } else {
        this.getHiddenInstanceField("wifiConfiguration").get(this) as WifiConfiguration
    }
}

@Suppress("DEPRECATION")
fun WifiConfiguration.setMacRandomizationSettingCompat(macRandomizationSetting: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.macRandomizationSetting = macRandomizationSetting
    } else {
        this.getHiddenInstanceField("macRandomizationSetting").setInt(this, macRandomizationSetting)
    }
}

@Suppress("DEPRECATION")
@SuppressLint("InlinedApi")
fun WifiNetworkSuggestion.setMacRandomizationSetting(macRandomizationSetting: Int) {
    val wifiConfiguration = this.getWifiConfiguration()
    val wifiConfigurationMacRandomizationSetting =
        if (macRandomizationSetting == WifiNetworkSuggestion.RANDOMIZATION_NON_PERSISTENT) WifiConfiguration.RANDOMIZATION_NON_PERSISTENT else WifiConfiguration.RANDOMIZATION_PERSISTENT
    wifiConfiguration.setMacRandomizationSettingCompat(wifiConfigurationMacRandomizationSetting)
}

@Suppress("Deprecation")
fun WifiNetworkSuggestion.getSSIDCompat(): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return this.ssid!!
    val wifiConfiguration = this.getWifiConfiguration()
    return wifiConfiguration.SSID
}

fun resetNetworkSuggestionMacAddress(ssid: String): Boolean {
    val suggestion = supportedSSIDSuggestions.singleOrNull { it.getSSIDCompat() == ssid }
    if (suggestion == null) return false
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
    suggestion.forEach { it.setMacRandomizationSetting(WifiNetworkSuggestion.RANDOMIZATION_NON_PERSISTENT) }
    val addStatus = wifiManager.addNetworkSuggestions(suggestion)
    log("resetNetworkSuggestionMacAddress addStatus=${addStatus.toNetworkSuggestionStatusString()}")
    return addStatus == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
}
