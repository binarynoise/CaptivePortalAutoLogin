package de.binarynoise.captiveportalautologin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.CreateMethod
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityMainBinding
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
            if (ConnectivityChangeListenerService.running.get()) {
                stopService(Intent(this, ConnectivityChangeListenerService::class.java))
            }
            ContextCompat.startForegroundService(this, Intent(this, ConnectivityChangeListenerService::class.java))
        }
        binding.stopServiceButton.setOnClickListener {
            stopService(Intent(this, ConnectivityChangeListenerService::class.java))
        }
        binding.managePermissionsButton.setOnClickListener {
            startActivity(Intent(this, PermissionActivity::class.java))
        }
        binding.captureLoginButton.setOnClickListener {
            startActivity(Intent(this, GeckoViewActivity::class.java))
        }
        binding.exportLogsButton.setOnClickListener {
            startActivity(Intent(this, LogExportActivity::class.java))
        }
    }
    
    override fun onResume() {
        super.onResume()
        with(binding.serviceRunningText) {
            if (ConnectivityChangeListenerService.running.get()) {
                text = "Service is currently running"
            } else {
                text = "Service is currently not running"
            }
        }
    }
}
