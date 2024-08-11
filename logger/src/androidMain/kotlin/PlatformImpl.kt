package de.binarynoise.logger

import android.os.BaseBundle
import android.util.Log
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.util.SparseIntArray
import android.util.SparseLongArray
import android.view.View
import android.view.ViewGroup
import androidx.collection.SparseArrayCompat
import androidx.collection.forEach
import androidx.core.util.forEach
import androidx.core.util.isEmpty
import androidx.core.view.children
import de.binarynoise.logger.Logger.dump
import org.json.JSONArray
import org.json.JSONObject

class PlatformImpl : Platform {
    private val TAG = "Logger"
    
    var printBuffer = StringBuilder()
    override fun <T> print(t: T) {
        printBuffer.append(t)
    }
    
    override fun <T> println(t: T) {
        print(t)
        printBuffer.toString().lineSequence().forEach {
            Log.d(TAG, it)
        }
        printBuffer.clear()
    }
    
    var printErrBuffer = StringBuilder()
    override fun <T> printErr(t: T) {
        printErrBuffer.append(t)
    }
    
    override fun <T> printlnErr(t: T) {
        printErr(t)
        printErrBuffer.toString().lineSequence().forEach {
            Log.e(TAG, it)
        }
        printErrBuffer.clear()
    }
    
    
    override fun platformSpecificDump(
        obj: Any, name: String, nextIndent: Int, processed: MutableSet<Any>, forceInclude: Set<Any>, forceIncludeClasses: Set<Class<*>>
    ): Boolean = with(obj) {
        when {
            this is SparseArray<*> -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEach { k, v -> v.dump(k.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                }
            }
            this is SparseIntArray -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEach { k, v -> v.dump(k.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                }
            }
            this is SparseLongArray -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEach { k, v -> v.dump(k.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                }
            }
            this is SparseBooleanArray -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEach { k, v -> v.dump(k.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                }
            }
            this is SparseArrayCompat<*> -> {
                if (this.isEmpty) {
                    println("[]")
                } else {
                    println()
                    this.forEach { k, v -> v.dump(k.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                }
            }
            this is JSONObject -> {
                println()
                this.keys().forEach {
                    this.get(it).dump(it, nextIndent, processed, forceInclude, forceIncludeClasses)
                }
            }
            this is JSONArray -> {
                println()
                for (i in 0 until this.length()) {
                    this.get(i).dump(i.toString(), nextIndent, processed, forceInclude, forceIncludeClasses)
                }
            }
            this is BaseBundle -> {
                val keys = keySet()
                if (keys.isNullOrEmpty()) {
                    println("[]")
                } else {
                    println()
                    keys.forEach {
                        @Suppress("DEPRECATION") get(it).dump(it, nextIndent, processed, forceInclude, forceIncludeClasses)
                    }
                }
            }
            this is View.BaseSavedState -> {
                println(this.toString())
            }
            this is ViewGroup -> {
                println()
                children.forEachIndexed { view, i -> view.dump(i.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
            }
            
            else -> return@with false
        }
        
        return@with true
    }
    
    val backgroundHandler = createBackgroundHandler()
    
    override fun runInBackground(block: () -> Unit) {
        backgroundHandler.post(block)
    }
}

internal actual val platform: Platform = PlatformImpl()