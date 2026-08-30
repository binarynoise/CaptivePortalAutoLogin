package de.binarynoise.captiveportalautologin

import java.util.function.IntFunction
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.binarynoise.captiveportalautologin.util.startActivity
import de.binarynoise.liberator.cast

@Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")
class Permission private constructor(
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val granted: (Context) -> Boolean,
    val request: (ComponentActivity) -> Unit,
    val enabled: (Context) -> Boolean,
) {
    constructor(
        @StringRes nameRes: Int,
        @StringRes descriptionRes: Int,
        granted: (Context) -> Boolean,
        request: (ComponentActivity) -> Unit,
        enabled: (Context) -> Boolean = { true },
        minSdk: Int = 0,
    ) : this(
        nameRes,
        descriptionRes,
        if (minSdk == 0) granted else { context -> (Build.VERSION.SDK_INT < minSdk) || granted(context) },
        if (minSdk == 0) request else { componentActivity ->
            if (Build.VERSION.SDK_INT >= minSdk) request(componentActivity)
        },
        if (minSdk == 0) enabled else { context -> (Build.VERSION.SDK_INT >= minSdk) && enabled(context) },
    )
}

private val allPermissions = mutableSetOf<Permission>()

@SuppressLint("InlinedApi")
object Permissions : Set<Permission> by allPermissions {
    val locationPermissions = mutableSetOf<Permission>()
    
    val notifications = Permission(
        R.string.preference_permission_notifications,
        R.string.preference_permission_notifications_description,
        { context ->
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            ActivityCompat.requestPermissions(componentActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        },
        minSdk = Build.VERSION_CODES.TIRAMISU,
    )
    
    val fineLocation = Permission(
        R.string.preference_permission_fine_location,
        R.string.preference_permission_fine_location_description,
        { context ->
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            componentActivity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        },
        minSdk = Build.VERSION_CODES.O,
    )
    
    val backgroundLocation = Permission(
        R.string.preference_permission_background_location,
        R.string.preference_permission_background_location_description,
        { context ->
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            ActivityCompat.requestPermissions(
                componentActivity, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0
            )
        },
        { context ->
            fineLocation.granted(context)
        },
        minSdk = Build.VERSION_CODES.Q,
    )
    
    val locationEnabled = Permission(
        R.string.preference_permission_location,
        R.string.preference_permission_location_description,
        { context ->
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                LocationManager::class.java.declaredFields.filter { it.name.endsWith("_PROVIDER") }
                    .map { it.get(null) as String }
                    .map { locationManager.isProviderEnabled(it) }
                    .any { it } // 
                    && Settings.Secure.getInt(context.contentResolver, "location_mode", 0) > 0
            }
        },
        { componentActivity ->
            componentActivity.startActivity { action = Settings.ACTION_LOCATION_SOURCE_SETTINGS }
        },
        minSdk = Build.VERSION_CODES.O,
    )
    
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Deprecated("Deprecated in Java for some reason")
    override fun <T : Any> toArray(generator: IntFunction<Array<out T>>): Array<out T> {
        return this.cast<java.util.Set<*>>().toArray(generator.apply(0))
    }
    
    init {
        allPermissions.add(notifications)
        allPermissions.add(fineLocation)
        allPermissions.add(backgroundLocation)
        allPermissions.add(locationEnabled)
        locationPermissions.add(fineLocation)
        locationPermissions.add(backgroundLocation)
        locationPermissions.add(locationEnabled)
    }
}
