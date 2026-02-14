package de.binarynoise.captiveportalautologin.preferences

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.liberator.PortalDetection
import de.binarynoise.liberator.PortalTestURL

object SharedPreferences {
    val liberator_automatically_liberate: PreferencePropertyDelegate<Boolean> by PreferenceProperty(true)
    val liberator_captive_test_url: DynamicPreferencePropertyDelegate<String, PortalTestURL> by DynamicPreferenceProperty(
        { key -> PortalDetection.backends.getValue(key) },
        { value -> PortalDetection.backends.entries.single { it.value == value }.key },
        PortalDetection.defaultBackendKey,
    )
    val liberator_user_agent: PreferencePropertyDelegate<String> by PreferenceProperty(PortalDetection.defaultUserAgent)
    val liberator_send_stats: PreferencePropertyDelegate<Boolean> by PreferenceProperty(true)
    val api_base: PreferencePropertyDelegate<String> by PreferenceProperty("")
    
    val stats_last_retry_time by PreferenceProperty(0L)
    
    private class PreferenceProperty<T : Any>(private val defaultValue: T) {
        operator fun getValue(parent: Any, property: KProperty<*>): PreferencePropertyDelegate<T> {
            return PreferencePropertyDelegate(property, defaultValue)
        }
    }
    
    private class DynamicPreferenceProperty<K : Any, V>(
        private val getValue: (K) -> V,
        private val getKey: (V) -> K,
        private val defaultKey: K,
    ) {
        operator fun getValue(parent: Any, property: KProperty<*>): DynamicPreferencePropertyDelegate<K, V> {
            return DynamicPreferencePropertyDelegate(property, getValue, getKey, defaultKey)
        }
    }
}


open class PreferencePropertyDelegate<T : Any>(val parent: KProperty<*>, val defaultValue: T) :
    ReadWriteProperty<Any?, T?>, (Preference) -> Unit {
    val key = parent.name
    
    init {
        require(defaultValue is Int || defaultValue is String || defaultValue is Boolean || defaultValue is Float || defaultValue is Long) {
            "Unsupported type: ${defaultValue::class}"
        }
    }
    
    override fun invoke(preference: Preference) {
        preference.setDefaultValue(defaultValue)
        preference.key = key
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

class DynamicPreferencePropertyDelegate<K : Any, V>(
    val parent: KProperty<*>,
    private val getValue: (K) -> V,
    private val getKey: (V) -> K,
    val defaultKey: K,
) : ReadWriteProperty<Any?, V>, (Preference) -> Unit {
    val wrapped = PreferencePropertyDelegate(parent, defaultKey)
    
    val key: String by wrapped::key
    
    
    override fun invoke(preference: Preference) {
        preference.setDefaultValue(getValue(defaultKey))
        preference.key = key
    }
    
    @JvmName("getValueNullable")
    operator fun getValue(thisRef: Any?, property: KProperty<*>?): V {
        return getValue(wrapped.get())
    }
    
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return getValue(thisRef, null)
    }
    
    @JvmName("setKeyNullable")
    operator fun setValue(thisRef: Any?, property: KProperty<*>?, newKey: K) {
        wrapped.set(newKey)
    }
    
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        setValue(thisRef, null, value)
    }
    
    @JvmName("setValueNullable")
    operator fun setValue(thisRef: Any?, property: KProperty<*>?, newValue: V) {
        setValue(thisRef, property, getKey(newValue))
    }
    
    fun get(): V = getValue(null, null)
    
    @JvmName("setKey")
    fun set(newKey: K) = setValue(null, null, newKey)
    
    @JvmName("setValue")
    fun set(newValue: V) = setValue(null, null, getKey(newValue))
}
