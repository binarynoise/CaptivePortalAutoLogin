package de.binarynoise.captiveportalautologin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityPermissionsBinding
import de.binarynoise.captiveportalautologin.databinding.ItemPermissionBinding

class PermissionActivity : ComponentActivity() {
    private val binding: ActivityPermissionsBinding by viewBinding(CreateMethod.INFLATE)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        binding.permissionList.adapter = object : ArrayAdapter<Permission>(this, R.layout.item_permission, permissions) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view: View;
                val binding: ItemPermissionBinding;
                
                if (convertView == null) {
                    binding = ItemPermissionBinding.inflate(layoutInflater, parent, false)
                    view = binding.root
                } else {
                    view = convertView
                    binding = ItemPermissionBinding.bind(view)
                }
                
                val permission = getItem(position)
                
                with(binding) {
                    if (permission == null) {
                        nameText.text = ""
                        descriptionText.text = ""
                        grantedCheckBox.isChecked = false
                        requestButton.setOnClickListener(null)
                    } else {
                        nameText.text = permission.name ?: getString(permission.nameRes ?: 0)
                        descriptionText.text = permission.description ?: getString(permission.descriptionRes ?: 0)
                        grantedCheckBox.isChecked = permission.granted(context)
                        requestButton.setOnClickListener { permission.request(this@PermissionActivity) }
                    }
                }
                
                
                return view
            }
        }
    }
    
    companion object {
        val permissions: List<Permission> = listOf(
            Permission(
                "Send Notifications", "Show a persistent status notification",
                { context ->
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || //
                            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED
                },
                { componentActivity ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        return@Permission
                    }
                    
                    ActivityCompat.requestPermissions(componentActivity, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
                },
            ),
            Permission(
                "show toast messages", "Show little messages at the bottom of the screen",
                { context ->
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.SYSTEM_ALERT_WINDOW) == PERMISSION_GRANTED
                },
                {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                    it.startActivity(intent)
                },
            ),
        )
    }
    
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
}
