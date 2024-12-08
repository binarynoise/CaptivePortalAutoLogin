@file:SuppressLint("PrivateApi")

package de.binarynoise.captiveportalautologin.xposed

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.ContextCompat
import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.logger.Logger
import de.binarynoise.logger.Logger.log
import de.binarynoise.logger.PlatformImpl.Companion.toXposedBridge
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodHook as MethodHook


class ReevaluationHook : IXposedHookLoadPackage {
    val receiver = ReevaluationReceiver()
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) return
        
        Logger.Config.apply {
            toSOut = true
            
            toXposedBridge = BuildConfig.DEBUG
        }
        
        log("handleLoadPackage ${lpparam.packageName} with process ${lpparam.processName} and pid ${android.os.Process.myPid()}")
        
        try {
            val ConnectivityManagerClass = Class.forName("android.net.ConnectivityManager", false, lpparam.classLoader)
            
            XposedBridge.hookAllConstructors(
                ConnectivityManagerClass,
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) = with(param) {
                        if (instance != null) return
                        
                        val icm = args[1]
                        if (icm::class.qualifiedName?.contains("Proxy") == true) return
                        if (icm::class.java.declaredFields.none { it -> it.name == "mNetworkAgentInfos" }) return
                        instance = icm
                        
                        val context: Context = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
                        log("Registered receiver in package: ${lpparam.packageName}, process: ${lpparam.processName}")
                    }
                },
            )
        } catch (_: ClassNotFoundException) {
        } catch (_: XposedHelpers.ClassNotFoundError) {
        } catch (e: Throwable) {
            log("failed to hook ConnectivityManager", e)
        }
    }
    
    companion object {
        var instance: Any? = null // ConnectivityService : IConnectivityManager
        const val ACTION = "de.binarynoise.captiveportalautologin.xposed.Hook.ACTION"
    }
}

class ReevaluationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        log("received Broadcast: $intent")
        try {
            val instance = ReevaluationHook.instance ?: run { log("instance is null"); return }
            
            val mNetworkAgentInfos = XposedHelpers.getObjectField(instance, "mNetworkAgentInfos")
            var networkAgentInfos: Collection<*> = when (mNetworkAgentInfos) {
                is Set<*> -> {
                    // private final ArraySet<NetworkAgentInfo> mNetworkAgentInfos = new ArraySet<>();
                    mNetworkAgentInfos
                }
                is Map<*, *> -> {
                    // private final HashMap<Messenger, NetworkAgentInfo> mNetworkAgentInfos = new HashMap<>();
                    mNetworkAgentInfos.values
                }
                else -> {
                    emptySet<Any>()
                }
            }
            
            for (nai in networkAgentInfos) {
                val nm = XposedHelpers.callMethod(nai, "networkMonitor")
                XposedHelpers.callMethod(nm, "forceReevaluation", -1 /* INVALID_UID */)
            }
            
            log("sent message to force reevaluation")
            Toast.makeText(context, "Forcing reevaluation", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            XposedBridge.log(e)
            log("failed to force reevaluation", e)
        }
    }
}