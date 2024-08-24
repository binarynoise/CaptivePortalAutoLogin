package de.binarynoise.captiveportalautologin.preferences

import kotlin.reflect.KProperty
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import de.binarynoise.captiveportalautologin.util.applicationContext

object SharedPreferences {
    val liberator_automatically_liberate by PreferenceProperty<Boolean>(true)
}

class PreferenceProperty<T>(private val defaultValue: T) {
    
    operator fun getValue(parent: Any, property: KProperty<*>): Inner<T> {
        return Inner(property.name, defaultValue)
    }
    
    class Inner<T>(val key: String, val defaultValue: T) : (Preference) -> Unit {
        override fun invoke(preference: Preference) {
            preference.setDefaultValue(defaultValue)
            preference.key = key
        }
        
        @Suppress("UNCHECKED_CAST")
        operator fun getValue(parent: Any, property: KProperty<*>): T {
            with(PreferenceManager.getDefaultSharedPreferences(applicationContext)) {
                return if (contains(key)) all[key] as T
                else defaultValue
            }
        }
        
        operator fun setValue(parent: Any?, property: KProperty<*>, newValue: T?) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext).edit {
                when (newValue) {
                    null -> remove(key)
                    is Int -> putInt(key, newValue)
                    is String -> putString(key, newValue)
                    is Boolean -> putBoolean(key, newValue)
                    is Float -> putFloat(key, newValue)
                    is Long -> putLong(key, newValue)
                    else -> {
                        @Suppress("USELESS_CAST") //
                        throw IllegalArgumentException("Cannot save " + (newValue as Any)::class.qualifiedName + " into SharedPreferences")
                    }
                }
            }
        }
    }
}
