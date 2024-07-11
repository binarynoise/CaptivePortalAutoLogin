package de.binarynoise.captiveportalautologin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return@Permission true
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return@Permission
            }
            
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@Permission true
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        },
        { componentActivity ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@Permission
            if (!fineLocation.granted(componentActivity)) {
                Toast.makeText(componentActivity, "Cannot request background location without fine location permission", Toast.LENGTH_LONG).show()
                return@Permission
            }
            
            ActivityCompat.requestPermissions(componentActivity, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0)
        },
    )
    
    val all: List<Permission> = listOf(
        notifications,
        fineLocation,
        backgroundLocation,
    )
}
