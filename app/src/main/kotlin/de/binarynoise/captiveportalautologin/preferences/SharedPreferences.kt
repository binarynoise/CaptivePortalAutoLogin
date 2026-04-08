package de.binarynoise.captiveportalautologin.preferences

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import android.provider.Settings
import androidx.core.content.edit
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
    val liberator_captive_test_url_key: PreferencePropertyDelegate<String> by PreferenceProperty(PortalDetection.backendsAndroid.keys.first())
    val liberator_captive_test_url: MappedPreferencePropertyDelegate<String, PortalTestURL> =
        liberator_captive_test_url_key.map { key ->
            PortalDetection.backendsAndroid[key] ?: error("invalid portal backend")
        }
    val liberator_user_agent_key: PreferencePropertyDelegate<String> by PreferenceProperty(PortalDetection.userAgentsAndroid.keys.first())
    val liberator_user_agent: MappedPreferencePropertyDelegate<String, String> = liberator_user_agent_key.map { key ->
        PortalDetection.userAgentsAndroid[key] ?: error("invalid user agent")
    }
    val liberator_send_stats: PreferencePropertyDelegate<Boolean> by PreferenceProperty(true)
    val api_base: PreferencePropertyDelegate<String> by PreferenceProperty("")
    val network_suggestions: PreferencePropertyDelegate<Boolean> by PreferenceProperty(false)
    val network_suggestions_mac_randomization: PreferencePropertyDelegate<Boolean> by PreferenceProperty(false)
    
    val stats_last_retry_time by PreferenceProperty(0L)
    
    private class PreferenceProperty<T : Any>(private val defaultValue: T) {
        operator fun getValue(parent: Any, property: KProperty<*>): PreferencePropertyDelegate<T> {
            return PreferencePropertyDelegate(property, defaultValue)
        }
    }
}


open class PreferencePropertyDelegate<T : Any>(val parent: KProperty<*>, val defaultValue: T) :
    ReadWriteProperty<Any?, T?> {
    val key = parent.name
    
    init {
        require(defaultValue is Int || defaultValue is String || defaultValue is Boolean || defaultValue is Float || defaultValue is Long) {
            "Unsupported type: ${defaultValue::class}"
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    @JvmName("getValueNullable")
    operator fun getValue(thisRef: Any?, property: KProperty<*>?): T {
        with(PreferenceManager.getDefaultSharedPreferences(applicationContext)) {
            return if (contains(key)) all[key] as T
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
                null -> remove(key)
                is Int -> putInt(key, newValue)
                is String -> putString(key, newValue)
                is Boolean -> putBoolean(key, newValue)
                is Float -> putFloat(key, newValue)
                is Long -> putLong(key, newValue)
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

class MappedPreferencePropertyDelegate<W : Any, T : Any>(
    val wrapped: PreferencePropertyDelegate<W>,
    val transform: (W) -> T,
) : ReadOnlyProperty<Any?, T> {
    @JvmName("getValueNullable")
    operator fun getValue(thisRef: Any?, property: KProperty<*>?): T {
        return transform(wrapped.getValue(thisRef, null))
    }
    
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return getValue(thisRef, null)
    }
    
    fun get(): T = getValue(null, null)
}

fun <W : Any, T : Any> PreferencePropertyDelegate<W>.map(transform: (W) -> T): MappedPreferencePropertyDelegate<W, T> {
    return MappedPreferencePropertyDelegate(this, transform)
}
