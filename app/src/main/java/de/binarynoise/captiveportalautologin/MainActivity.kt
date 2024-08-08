package de.binarynoise.captiveportalautologin

import kotlin.concurrent.read
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible

import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.NetworkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.ServiceState
import de.binarynoise.captiveportalautologin.databinding.ActivityMainBinding
import de.binarynoise.captiveportalautologin.util.startActivity
import de.binarynoise.logger.Logger.log

class MainActivity : ComponentActivity() {
    private val binding by viewBinding { ActivityMainBinding.inflate(layoutInflater) }
    
    @Suppress("UNUSED_PARAMETER")
    fun updateStatusText(oldState: ServiceState?, newState: ServiceState) {
//        log("received service state: $newState")
        runOnUiThread {
            with(binding) {
                serviceStateText.text = newState.toString()
            }
        }
    }
    
    @Suppress("UNUSED_PARAMETER")
    fun updateNetworkText(oldState: NetworkState?, newState: NetworkState?) {
        runOnUiThread {
            with(binding) {
                networkStateText.text = newState.toString()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        ConnectivityChangeListenerService.serviceListeners.add(::updateStatusText)
        ConnectivityChangeListenerService.networkListeners.add(::updateNetworkText)
        
        with(binding) {
            startServiceButton.setOnClickListener {
                ConnectivityChangeListenerService.restart()
            }
            stopServiceButton.setOnClickListener {
                ConnectivityChangeListenerService.stop()
            }
            managePermissionsButton.setOnClickListener {
                startActivity<PermissionActivity>()
            }
            captureLoginButton.setOnClickListener {
                startActivity<GeckoViewActivity>()
            }
            exportLogsButton.setOnClickListener {
                startActivity<LogExportActivity>()
            }
            
            val deviceIs64Bit = Build.SUPPORTED_ABIS.any { abi -> abi.contains("64") }
            val librariesAre64Bit = applicationInfo.nativeLibraryDir.contains("64")
            log("device is 64 bit: $deviceIs64Bit, libraries are 64 bit: $librariesAre64Bit")
            abiMismatchError.isVisible = deviceIs64Bit != librariesAre64Bit
        }
        
        if (intent.getBooleanExtra("startService", true)) {
            ConnectivityChangeListenerService.start()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateStatusText(null, ConnectivityChangeListenerService.serviceStateLock.read { ConnectivityChangeListenerService.serviceState })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ConnectivityChangeListenerService.serviceListeners.remove(::updateStatusText)
        ConnectivityChangeListenerService.networkListeners.remove(::updateNetworkText)
    }
}
