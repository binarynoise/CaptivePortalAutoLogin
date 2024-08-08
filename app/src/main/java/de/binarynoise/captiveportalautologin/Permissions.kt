package de.binarynoise.captiveportalautologin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast
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
    val request: (ComponentActivity) -> Unit
) {
    constructor(name: String, description: String, granted: (Context) -> Boolean, request: (ComponentActivity) -> Unit) : this(
        name, null, description, null, granted, request
    )
    
    constructor(nameRes: Int, descriptionRes: Int, granted: (Context) -> Boolean, request: (ComponentActivity) -> Unit) : this(
        null, nameRes, null, descriptionRes, granted, request
    )
}

object Permissions {
    val notifications = Permission(
        "Send Notifications", "Show a persistent status notification and show little messages at the bottom of the screen",
        { context ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@Permission true
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@Permission
            
            ActivityCompat.requestPermissions(componentActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        },
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
        "Background Location",
        "Collect the SSID of Portals. Required for the background service",
        { context ->
            if (Build.VERSION.SDK_INT < 34) return@Permission true
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            if (Build.VERSION.SDK_INT < 29) return@Permission
            if (!fineLocation.granted(componentActivity)) {
                Toast.makeText(componentActivity, "Cannot request background location without fine location permission", Toast.LENGTH_LONG).show()
                return@Permission
            }
            
            ActivityCompat.requestPermissions(componentActivity, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0)
        },
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
    
    val all: List<Permission> = listOf(
        notifications,
        fineLocation,
        backgroundLocation,
        openSettings,
    )
}
