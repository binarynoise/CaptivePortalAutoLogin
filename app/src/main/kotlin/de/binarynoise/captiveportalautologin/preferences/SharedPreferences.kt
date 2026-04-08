package de.binarynoise.captiveportalautologin.preferences

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import androidx.preference.DropDownPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.getSystemApiStaticField
import de.binarynoise.liberator.PortalDetection
import de.binarynoise.liberator.PortalTestURL
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

val SystemPortalTestUrl = PortalTestURL(
    httpUrl = Settings.Global.getString(
        applicationContext.contentResolver,
        (getSystemApiStaticField(Settings.Global::class.java, "CAPTIVE_PORTAL_HTTP_URL") as String)
    )?.toHttpUrlOrNull() ?: PortalDetection.backends["Google"]?.httpUrl ?: PortalDetection.defaultBackend.httpUrl,
    httpsUrl = Settings.Global.getString(
        applicationContext.contentResolver,
        (getSystemApiStaticField(Settings.Global::class.java, "CAPTIVE_PORTAL_HTTPS_URL") as String)
    )?.toHttpUrlOrNull() ?: PortalDetection.backends["Google"]?.httpsUrl ?: PortalDetection.defaultBackend.httpsUrl,
)
val PortalDetection.backendsAndroid: Map<String, PortalTestURL>
    get() = mapOf("System" to SystemPortalTestUrl) + PortalDetection.backends

val SystemPortalUserAgent = Settings.Global.getString(
    applicationContext.contentResolver,
    (getSystemApiStaticField(Settings.Global::class.java, "CAPTIVE_PORTAL_USER_AGENT") as String)
) ?: PortalDetection.userAgents["AOSP"] ?: PortalDetection.defaultUserAgent
val PortalDetection.userAgentsAndroid: Map<String, String>
    get() = mapOf("System" to SystemPortalUserAgent) + PortalDetection.userAgents

object SharedPreferences {
    val liberator_automatically_liberate: PreferencePropertyDelegate<Boolean> by PreferenceProperty(true)
    
    val liberator_captive_test_url: MappedPreferencePropertyDelegate<PortalTestURL> by MappedPreferenceProperty(
        PortalDetection.backendsAndroid.keys.first(),
        PortalDetection.backendsAndroid,
    )
    
    val liberator_user_agent: MappedPreferencePropertyDelegate<String> by MappedPreferenceProperty(
        PortalDetection.userAgentsAndroid.keys.first(),
        PortalDetection.userAgentsAndroid,
    )
    
    val liberator_send_stats: PreferencePropertyDelegate<Boolean> by PreferenceProperty(true)
    val api_base: PreferencePropertyDelegate<String> by PreferenceProperty("")
    val network_suggestions: PreferencePropertyDelegate<Boolean> by PreferenceProperty(false)
    val network_suggestions_mac_randomization: PreferencePropertyDelegate<Boolean> by PreferenceProperty(false)
    
    val stats_last_retry_time by PreferenceProperty(0L)
    
    private class PreferenceProperty<T : Any>(private val defaultValue: T) {
        operator fun getValue(parent: Any, property: KProperty<*>): PreferencePropertyDelegate<T> {
            return PreferencePropertyDelegate(property.name, defaultValue)
        }
    }
    
    private class MappedPreferenceProperty<T : Any>(val defaultKey: String, val hashMap: Map<String, T>) {
        operator fun getValue(parent: Any, property: KProperty<*>): MappedPreferencePropertyDelegate<T> {
            val wrapped: PreferencePropertyDelegate<String> = PreferencePropertyDelegate(property.name, defaultKey)
            return MappedPreferencePropertyDelegate(wrapped, hashMap)
        }
    }
}


open class PreferencePropertyDelegate<T : Any>(val sharedPreferencesKey: String, val defaultValue: T) :
    ReadWriteProperty<Any?, T?> {
    
    init {
        require(defaultValue is Int || defaultValue is String || defaultValue is Boolean || defaultValue is Float || defaultValue is Long) {
            "Unsupported type: ${defaultValue::class}"
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    @JvmName("getValueNullable")
    operator fun getValue(thisRef: Any?, property: KProperty<*>?): T {
        with(PreferenceManager.getDefaultSharedPreferences(applicationContext)) {
            return if (contains(sharedPreferencesKey)) all[sharedPreferencesKey] as T
            else defaultValue
        }
    }
    
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return getValue(thisRef, null)
    }
    
    @JvmName("setValueNullable")
    operator fun setValue(thisRef: Any?, property: KProperty<*>?, newValue: T?) {
        PreferenceManager.getDefaultSharedPreferences(applicationContext).edit {
            when (newValue) {
                null -> remove(sharedPreferencesKey)
                is Int -> putInt(sharedPreferencesKey, newValue)
                is String -> putString(sharedPreferencesKey, newValue)
                is Boolean -> putBoolean(sharedPreferencesKey, newValue)
                is Float -> putFloat(sharedPreferencesKey, newValue)
                is Long -> putLong(sharedPreferencesKey, newValue)
                else -> @Suppress("USELESS_CAST") throw IllegalArgumentException("Cannot save " + (newValue as Any)::class.qualifiedName + " into SharedPreferences")
            }
        }
    }
    
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        setValue(thisRef, null, value)
    }
    
    fun get(): T = getValue(null, null)
    
    fun set(newValue: T?) = setValue(null, null, newValue)
}

class MappedPreferencePropertyDelegate<V : Any>(
    val wrapped: PreferencePropertyDelegate<String>,
    val hashMap: Map<String, V>,
) : ReadOnlyProperty<Any?, V> {
    val sharedPreferencesKey get() = wrapped.sharedPreferencesKey
    val defaultKey get() = wrapped.defaultValue
    
    val defaultTransformedValue: V = hashMap.getValue(defaultKey)
    
    @JvmName("getValueNullable")
    operator fun getValue(thisRef: Any?, property: KProperty<*>?): V {
        return hashMap[wrapped.get()] ?: defaultTransformedValue
    }
    
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return getValue(thisRef, null)
    }
    
    fun get(): V = getValue(null, null)
    
    fun setupPreference(preference: ListPreference) {
        preference.key = sharedPreferencesKey
        preference.entries = hashMap.keys.toTypedArray()
        preference.entryValues = hashMap.keys.toTypedArray()
        preference.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        preference.setDefaultValue(defaultKey)
    }
}

fun <V : Any> DropDownPreference(
    context: Context,
    sharedPreference: MappedPreferencePropertyDelegate<V>,
): DropDownPreference {
    return DropDownPreference(context).apply(sharedPreference::setupPreference)
}
