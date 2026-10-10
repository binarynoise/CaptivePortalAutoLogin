package de.binarynoise.captiveportalautologin.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment

class BundleDelegate<in Receiver, BundleValue, Value>(
    private val bundleProvider: Receiver.() -> Bundle?,
    private val getter: Bundle.(String) -> BundleValue?,
    private val default: Value,
    private val transform: (BundleValue) -> Value,
) : ReadOnlyProperty<Receiver, Value> {
    override operator fun getValue(thisRef: Receiver, property: KProperty<*>): Value {
        val bundle = thisRef.bundleProvider() ?: return default
        val bundleKey = property.name
        if (!bundle.containsKey(bundleKey)) return default
        return transform(bundle.getter(bundleKey) ?: return default)
    }
}

fun <Value> fragmentArguments(
    getter: Bundle.(String) -> Value?,
    default: Value,
): BundleDelegate<Fragment, Value, Value> = BundleDelegate(Fragment::getArguments, getter, default) { it }

fun <BundleValue, Value> fragmentArguments(
    getter: Bundle.(String) -> BundleValue?,
    default: Value,
    transform: (BundleValue) -> Value,
): BundleDelegate<Fragment, BundleValue, Value> = BundleDelegate(Fragment::getArguments, getter, default, transform)

fun <Value> activityExtras(
    getter: Bundle.(String) -> Value?,
    default: Value,
): BundleDelegate<Activity, Value, Value> = BundleDelegate({ intent?.extras }, getter, default, { it })

fun <BundleValue, Value> activityExtras(
    getter: Bundle.(String) -> BundleValue?,
    default: Value,
    transform: (BundleValue) -> Value,
): BundleDelegate<Activity, BundleValue, Value> = BundleDelegate({ intent?.extras }, getter, default, transform)
