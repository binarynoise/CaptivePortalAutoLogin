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
    private val connectivityListener: (running: Boolean) -> Unit = { running ->
        log("received service running: $running")
        runOnUiThread {
            with(binding.serviceRunningText) {
                if (running) {
                    text = "Service is currently running"
                } else {
                    text = "Service is currently not running"
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        ConnectivityChangeListenerService.serviceListeners.add(connectivityListener)
        log("added service listener")
        
        binding.startServiceButton.setOnClickListener {
            if (ConnectivityChangeListenerService.running) {
                ConnectivityChangeListenerService.stop()
            }
            ConnectivityChangeListenerService.start()
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
        with(binding.serviceRunningText) {
            if (ConnectivityChangeListenerService.running) {
                text = "Service is currently running"
            } else {
                text = "Service is currently not running"
            }
        }
    }
}
