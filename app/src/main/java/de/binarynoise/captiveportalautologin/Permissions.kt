package de.binarynoise.captiveportalautologin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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
        enabled,
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

@SuppressLint("InlinedApi")
object Permissions {
    val notifications = Permission(
        "Send Notifications", "Show a persistent status notification and show little messages at the bottom of the screen",
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
            ActivityCompat.requestPermissions(componentActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        },
    )
    
    val backgroundLocation = Permission(
        "Background Location", "Collect the SSID of Portals. Required for the background service",
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
    
    val openSettings = Permission(
        "Open Settings",
        "Open the app settings",
        { context ->
            !ContextCompat.getSystemService(context, UserManager::class.java)!!.isUserAGoat
        },
        { componentActivity ->
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", componentActivity.packageName, null)
            }
            componentActivity.startActivity(intent)
        },
    )
    
    @SuppressLint("NewApi")
    val all: List<Permission> = listOf(
        notifications,
        fineLocation,
        backgroundLocation,
        openSettings,
    )
}
