@file:RequiresApi(Build.VERSION_CODES.Q)

package de.binarynoise.captiveportalautologin

import java.util.concurrent.TimeUnit
import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.binarynoise.captiveportalautologin.BuildConfig.API_BASE
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.getHiddenInstanceField
import de.binarynoise.captiveportalautologin.util.getSignaturePublicKey
import de.binarynoise.captiveportalautologin.util.invokeHiddenMethod
import de.binarynoise.filedb.FixedKeyJsonDB
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.isExperimental
import de.binarynoise.liberator.portals.allPortalLiberators
import de.binarynoise.liberator.tryOrDefault
import de.binarynoise.logger.Logger.log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val ssidJsonDB = FixedKeyJsonDB(applicationContext.noBackupFilesDir.toPath(), "NetworkSuggestionSSIDs")
var ssidDb: List<String>?
    get() = ssidJsonDB.loadOrNull()
    set(value) = ssidJsonDB.storeOrDelete(value)

val supportedSSIDs: List<String>
    get() = ssidDb ?: allPortalLiberators.filter { !it.isExperimental() || BuildConfig.DEBUG }
        .flatMap { portalLiberator ->
            portalLiberator::class.java.annotations.filterIsInstance<SSID>().flatMap { it.ssid.asIterable() }
        }

@get:SuppressLint("InlinedApi")
val supportedSSIDSuggestions
    get() = supportedSSIDs.map { ssid ->
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

class UpdateNetworkSuggestionSSIDsWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        
        if (!SharedPreferences.network_suggestions.get()) {
            dequeueUpdateNetworkSuggestionSSIDsWork(appContext)
            return Result.success()
        }
        
        val apiBaseFromPreference by SharedPreferences.api_base
        val apiBaseUrl = (apiBaseFromPreference.takeUnless { it == "" } ?: API_BASE).toHttpUrlOrNull()
        if (apiBaseUrl == null) return Result.failure()
        val apiClient = ApiClient(apiBaseUrl, appContext.getSignaturePublicKey())
        
        log("obtaining ssids from api")
        val limit = wifiManager.maxNumberOfNetworkSuggestionsPerApp
        ssidDb = apiClient.getSSIDs(limit, BuildConfig.VERSION_CODE)
        log("got ${ssidDb?.size} ssids")
        sendNetworkSuggestions()
        return Result.success()
    }
}

private const val UpdateNetworkSuggestionSSIDsWorkerUniqueWorkName = "UpdateNetworkSuggestionSSIDs"
fun enqueueUpdateNetworkSuggestionSSIDsWork(
    context: Context = applicationContext,
    repeatInterval: Long = 7,
    repeatIntervalTimeUnit: TimeUnit = TimeUnit.DAYS,
    expedited: Boolean = false
) {
    log("enqueue ssid work")
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
    val workRequest =
        PeriodicWorkRequestBuilder<UpdateNetworkSuggestionSSIDsWorker>(repeatInterval, repeatIntervalTimeUnit).apply {
            setConstraints(constraints)
            if (expedited) setInitialDelay(repeatInterval, repeatIntervalTimeUnit)
        }.build()
    val workManager = WorkManager.getInstance(context)
    workManager.enqueueUniquePeriodicWork(
        UpdateNetworkSuggestionSSIDsWorkerUniqueWorkName,
        ExistingPeriodicWorkPolicy.UPDATE,
        workRequest,
    )
    if (expedited) {
        log("enqueue expedited ssid work")
        val workRequest = OneTimeWorkRequestBuilder<UpdateNetworkSuggestionSSIDsWorker>().apply {
            setConstraints(constraints)
            setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
        }.build()
        workManager.enqueue(workRequest)
    }
}

fun dequeueUpdateNetworkSuggestionSSIDsWork(context: Context = applicationContext) {
    WorkManager.getInstance(context).cancelUniqueWork(UpdateNetworkSuggestionSSIDsWorkerUniqueWorkName)
}

val NetworkSuggestionOnPreferenceChangeListener: Preference.OnPreferenceChangeListener = { preference, newValue ->
    require(preference is TwoStatePreference) { "preference is not TwoStatePreference" }
    if (newValue as Boolean) {
        enqueueUpdateNetworkSuggestionSSIDsWork(preference.context, expedited = true)
        sendNetworkSuggestions()
    } else {
        dequeueUpdateNetworkSuggestionSSIDsWork(preference.context)
        removeNetworkSuggestions()
    }
}

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

fun isNetworkSuggestion(ssid: String): Boolean {
    return tryOrDefault(false) {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.networkSuggestions.any { it.ssid == ssid }
        } else {
            supportedSSIDs.contains(ssid)
        }
    }
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
