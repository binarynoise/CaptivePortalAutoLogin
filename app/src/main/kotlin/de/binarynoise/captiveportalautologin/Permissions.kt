package de.binarynoise.captiveportalautologin

import java.util.function.*
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.binarynoise.captiveportalautologin.util.startActivity

@Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")
class Permission private constructor(
    val name: String?,
    @StringRes val nameRes: Int?,
    val description: String?,
    @StringRes val descriptionRes: Int?,
    val granted: (Context) -> Boolean,
    val request: (ComponentActivity) -> Unit,
    val enabled: (Context) -> Boolean,
) {
    private constructor(
        name: String?,
        @StringRes nameRes: Int?,
        description: String?,
        @StringRes descriptionRes: Int?,
        granted: (Context) -> Boolean,
        request: (ComponentActivity) -> Unit,
        enabled: (Context) -> Boolean = { true },
        minSdk: Int = 0,
    ) : this(
        name,
        nameRes,
        description,
        descriptionRes,
        if (minSdk == 0) granted else { context -> (Build.VERSION.SDK_INT < minSdk) || granted(context) },
        if (minSdk == 0) request else { componentActivity -> if (Build.VERSION.SDK_INT >= minSdk) request(componentActivity) },
        if (minSdk == 0) enabled else { context -> (Build.VERSION.SDK_INT >= minSdk) && enabled(context) },
    )
    
    constructor(
        name: String,
        description: String,
        granted: (Context) -> Boolean,
        request: (ComponentActivity) -> Unit,
        enabled: (Context) -> Boolean = { true },
        minSdk: Int = 0,
    ) : this(name, null, description, null, granted, request, enabled, minSdk)
    
    constructor(
        nameRes: Int,
        descriptionRes: Int,
        granted: (Context) -> Boolean,
        request: (ComponentActivity) -> Unit,
        enabled: (Context) -> Boolean = { true },
        minSdk: Int = 0,
    ) : this(null, nameRes, null, descriptionRes, granted, request, enabled, minSdk)
}

private val allPermissions = mutableSetOf<Permission>()

@SuppressLint("InlinedApi")
object Permissions : Set<Permission> by allPermissions {
    
    val notifications = Permission(
        "Send Notifications",
        "Show a persistent status notification and show little messages at the bottom of the screen",
        { context ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            ActivityCompat.requestPermissions(componentActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        },
        minSdk = Build.VERSION_CODES.TIRAMISU,
    )
    
    val fineLocation = Permission(
        "Fine Location",
        "Collect the SSID of Portals. Required for the background service",
        { context ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            componentActivity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        },
        minSdk = Build.VERSION_CODES.O,
    )
    
    val backgroundLocation = Permission(
        "Background Location",
        "Collect the SSID of Portals. Required for the background service",
        { context ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            ActivityCompat.requestPermissions(componentActivity, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0)
        },
        { context ->
            fineLocation.granted(context)
        },
        minSdk = Build.VERSION_CODES.Q,
    )
    
    val locationEnabled = Permission(
        "Location Enabled",
        "Collect the SSID of Portals. Required for the background service",
        { context ->
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isLocationEnabled
        },
        { componentActivity ->
            componentActivity.startActivity { action = Settings.ACTION_LOCATION_SOURCE_SETTINGS }
        },
        minSdk = Build.VERSION_CODES.O,
    )
    
    val openSettings = Permission(
        "Open Settings",
        "Open the app settings",
        { context ->
            !ContextCompat.getSystemService(context, UserManager::class.java)!!.isUserAGoat
        },
        { componentActivity ->
            componentActivity.startActivity {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", componentActivity.packageName, null)
            }
        },
    )
    
    @Deprecated("Deprecated in Java for some reason")
    override fun <T : Any> toArray(generator: IntFunction<Array<out T>>): Array<out T> {
        return (this as java.util.Set<*>).toArray(generator.apply(0))
    }
    
    init {
        allPermissions.add(notifications)
        allPermissions.add(fineLocation)
        allPermissions.add(backgroundLocation)
        allPermissions.add(locationEnabled)
        allPermissions.add(openSettings)
    }
}
