package de.binarynoise.captiveportalautologin

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityPermissionsBinding
import de.binarynoise.captiveportalautologin.databinding.ItemPermissionBinding

class PermissionActivity : ComponentActivity() {
    private val binding: ActivityPermissionsBinding by viewBinding(CreateMethod.INFLATE)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        binding.permissionList.adapter = object : ArrayAdapter<Permission>(this, R.layout.item_permission, Permissions.all) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view: View
                val binding: ItemPermissionBinding
                
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
                    
                    this@PermissionActivity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onResume(owner: LifecycleOwner) {
                            if (permission != null) {
                                grantedCheckBox.isChecked = permission.granted(context)
                            } else {
                                grantedCheckBox.isChecked = false
                            }
                        }
                    })
                }
                
                return view
            }
        }
    }
}
