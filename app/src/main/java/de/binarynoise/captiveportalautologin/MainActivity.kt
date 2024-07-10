package de.binarynoise.captiveportalautologin

import android.os.Bundle
import androidx.activity.ComponentActivity
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.ServiceState
import de.binarynoise.captiveportalautologin.databinding.ActivityMainBinding
import de.binarynoise.captiveportalautologin.util.startActivity

class MainActivity : ComponentActivity() {
    private val binding: ActivityMainBinding by viewBinding(CreateMethod.INFLATE)
    
    @Suppress("UNUSED_PARAMETER")
    fun updateStatusText(oldState: ServiceState?, newState: ServiceState) {
//        log("received service state: $newState")
        runOnUiThread {
            with(binding.serviceRunningText) {
                text = newState.toString()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        ConnectivityChangeListenerService.serviceListeners.add(::updateStatusText)
        
        binding.startServiceButton.setOnClickListener {
            ConnectivityChangeListenerService.restart()
        }
        binding.stopServiceButton.setOnClickListener {
            ConnectivityChangeListenerService.stop()
        }
        binding.managePermissionsButton.setOnClickListener {
            startActivity<PermissionActivity>()
        }
        binding.captureLoginButton.setOnClickListener {
            startActivity<GeckoViewActivity>()
        }
        binding.exportLogsButton.setOnClickListener {
            startActivity<LogExportActivity>()
        }
        
        if (intent.getBooleanExtra("startService", true)) {
            ConnectivityChangeListenerService.start()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateStatusText(null, ConnectivityChangeListenerService.serviceState)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ConnectivityChangeListenerService.serviceListeners.remove(::updateStatusText)
    }
}
