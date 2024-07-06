package de.binarynoise.captiveportalautologin

import android.os.Bundle
import androidx.activity.ComponentActivity
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityMainBinding
import de.binarynoise.captiveportalautologin.util.startActivity
import de.binarynoise.logger.Logger.log

class MainActivity : ComponentActivity() {
    private val binding: ActivityMainBinding by viewBinding(CreateMethod.INFLATE)
    
    // Don't inline
    fun updateStatusText(state: ConnectivityChangeListenerService.ServiceState) {
        log("received service state: $state")
        runOnUiThread {
            with(binding.serviceRunningText) {
                text = state.toString()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        ConnectivityChangeListenerService.serviceListeners.add(::updateStatusText)
        log("added service listener")
        
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
        updateStatusText(ConnectivityChangeListenerService.serviceState)
    }
}
