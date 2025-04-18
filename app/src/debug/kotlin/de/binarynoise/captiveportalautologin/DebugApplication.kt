package de.binarynoise.captiveportalautologin

import android.os.StrictMode
import android.os.StrictMode.VmPolicy

class DebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StrictMode.setVmPolicy(VmPolicy.Builder(StrictMode.getVmPolicy()).detectLeakedClosableObjects().build())
    }
}
