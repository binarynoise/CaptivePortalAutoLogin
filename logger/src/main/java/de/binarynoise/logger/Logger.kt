@file:Suppress(/*"unused",*/ "MemberVisibilityCanBePrivate")

package de.binarynoise.logger

import java.io.File
import java.lang.ref.Reference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass
import android.os.BaseBundle
import android.util.Log
import android.util.Log.getStackTraceString
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
import org.json.JSONArray
import org.json.JSONObject

object Logger {
    
    var DEBUG = true // TODO BuildConfig.DEBUG
    
    val backgroundHandler = createBackgroundHandler()
    
    fun log(message: CharSequence) {
        val callingClassTag = callingClassTag
        if (DEBUG) {
            message.toString().lines().forEach {
                Log.v("Logger", "$callingClassTag: $it")
            }
        }
        logToFile(message.toString(), "D", callingClassTag)
    }
    
    fun log(message: CharSequence, t: Throwable?) {
        val callingClassTag = callingClassTag
        val stackTraceString = getStackTraceString(t)
        if (DEBUG) {
            message.lines().forEach {
                Log.e("Logger", "$callingClassTag: $it:")
            }
            stackTraceString.lines().forEach {
                Log.e("Logger", it)
            }
        }
        logToFile("" + message + "\n" + stackTraceString, "E", callingClassTag)
    }
    
    val logFolder = applicationContext.filesDir.resolve("logs").apply { mkdir() }
    
    private fun logToFile(logString: String, level: String, callingClassTag: String) {
        val currentTimeString = currentTimeString
        backgroundHandler.post {
            try {
                logString.lines().forEach {
                    val file: File = logFolder.resolve("$currentDateString.log")
                    // TODO clear old logs
                    file.appendText("$currentTimeString $level $callingClassTag: $it\n")
                }
            } catch (e: Exception) {
                Log.e("Logger", null, e)
            }
        }
    }
    
    private val currentDateTimeString get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS", Locale.GERMAN).format(Date()).toString()
    private val currentDateString get() = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date()).toString()
    private val currentTimeString get() = SimpleDateFormat("HH:mm:ss,SSS", Locale.GERMAN).format(Date()).toString()
    
    fun Any?.dump(name: String, forceInclude: Set<Any> = emptySet(), forceIncludeClasses: Set<Class<*>> = emptySet()) {
        if (!DEBUG) return
        log("dumping $name")
        dump(name, 0, mutableSetOf(), forceInclude, forceIncludeClasses)
        System.out.flush()
    }
    
    private fun Any?.dump(name: String, indent: Int, processed: MutableSet<Any>, forceInclude: Set<Any>, forceIncludeClasses: Set<Class<*>>) {
        //<editor-fold defaultstate="collapsed" desc="...">
        if (!DEBUG) return
        
        val tabs = " ".repeat(indent * 2)
        val nextIndent = indent + 1
        print("$tabs$name ")
        if (this == null || this is Nothing? || this::class.qualifiedName == "null") {
            println("-> null")
            return
        }
        if (this is String) {
            print("-> \"")
            print(this)
            println("\"")
            return
        }
        if (this::class.javaPrimitiveType != null || this is CharSequence) {
            print("-> ")
            println(this.toString())
            return
        }
        
        print("(${this::class.qualifiedName}@${hashCode()}) -> ")
        
        if (processed.contains(this)) {
            println("already dumped")
            return
        }
        processed.add(this)
        
        when {
            indent > 10 -> {
                println("[...]")
            }
            this::class.java.isArray -> {
                if (this is Array<*>) { // Object Arrays
                    if (this.isEmpty()) {
                        println("[]")
                    } else {
                        println()
                        this.forEachIndexed { index, value -> value.dump(index.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                    }
                } else { // primitive Array like int[]
                    println(Arrays::class.java.getMethod("toString", this::class.java).invoke(null, this))
                }
            }
            //region SparseArrays
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
            //endregion
            this is Collection<*> -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEachIndexed { index, value -> value.dump(index.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                }
            }
            this is Map<*, *> -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEach { (k, v) -> v.dump(k.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
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
                        get(it).dump(it, nextIndent, processed, forceInclude, forceIncludeClasses)
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
            forceInclude.none { it == this } && forceIncludeClasses.none { it.isInstance(this) } && listOf(
                "android.content.Context",
                "android.view.View",
                "androidx.fragment.app.Fragment",
                "android.os.Handler",
                "android.content.res.Resources",
                "java.lang.Thread",
                "java.lang.ThreadGroup",
                "java.lang.ClassLoader",
                "android.content.res.ResourcesImpl",
                "android.content.res.ApkAssets",
                "kotlin.Function",
                "android.app.ActivityManager",
                "androidx.appcompat.app.AppCompatDelegate",
                "android.view.accessibility.AccessibilityManager",
                "android.view.View.AttachInfo",
                "java.lang.reflect.Member",
                "kotlinx.datetime.LocalDateTime",
                "j\$.time.LocalDateTime",
            ).any { this::class.qualifiedName == it } -> {
                println("i: $this")
            }
            this is Reference<*> -> {
//                println(get().toString())
                println()
                get().dump("referenced", nextIndent, processed, forceInclude, forceIncludeClasses)
            }
            this is Class<*> -> {
                println(this.canonicalName)
            }
            this is KClass<*> -> {
                println(this.java.canonicalName)
            }
            this::class.java.declaredFields.find { it.name.equals("INSTANCE", true) } != null -> {
                println("kotlin object")
                return
            }
            else -> {
                println()
                val fields = mutableSetOf<Field>()
                val methods = mutableSetOf<Method>()
                var cls: Class<*>? = this::class.java
                while (cls != null && cls != Any::class.java && cls != Object::class.java) {
                    fields.addAll(cls.declaredFields.filterNot { Modifier.isStatic(it.modifiers) })
                    methods.addAll(cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) && it.name.startsWith("get") && it.parameterCount == 0 })
                    cls = cls.superclass
                }
                
                fields.sortedBy { it.name }.forEach {
                    it.isAccessible = true
                    try {
                        it.get(this).dump(it.name, nextIndent, processed, forceInclude, forceIncludeClasses)
                    } catch (e: ReflectiveOperationException) {
                        e.printStackTrace()
                    }
                }
                
                val fieldNames = fields.map { it.name.lowercase() }.toSortedSet()
                methods -= methods.filter { it.name.removePrefix("get").lowercase() in fieldNames }.toSet()
                
                methods.sortedBy { it.name }.forEach {
                    it.isAccessible = true
                    try {
                        it.invoke(this).dump(it.name, nextIndent, processed, forceInclude, forceIncludeClasses)
                    } catch (e: ReflectiveOperationException) {
                        e.printStackTrace()
                    }
                }
            }
        }
//        processed.remove(this)
        //</editor-fold>
    }
    
    var buffer = StringBuilder()
    
    private fun <T> println(msg: T) {
        buffer.append(msg)
        log(buffer.toString())
        buffer.clear()
    }
    
    private fun println() {
        log(buffer.toString())
        buffer.clear()
    }
    
    private fun print(msg: Any) {
        buffer.append(msg)
    }
    
    fun View.dump(indent: Int = 0) {
        println(" ".repeat(indent * 2) + this)
        if (this is ViewGroup) {
            children.forEach {
                it.dump(indent + 1)
            }
        }
    }
    
    private val callingClassTag: String
        get() {
            val stackTraceElement = callingClassStackTraceElement
            
            val simpleClassName = stackTraceElement.simpleClassName
            return if (DEBUG) {
                val lineNumber = stackTraceElement.lineNumber
                val file = stackTraceElement.fileName ?: "unknown"
//                val substringAfterLast = proc.substringAfterLast(":", missingDelimiterValue = "x")
//                val proc = if (substringAfterLast != "x") "$substringAfterLast:" else ""
//                val thread = Thread.currentThread().name
                simpleClassName.padEnd(35) + " " + (" ($file:$lineNumber)").padStart(45)
            } else simpleClassName
        }
    
    private val StackTraceElement.simpleClassName: String
        get() = className.split("$").first().split(".").last()
    
    private val callingClassStackTraceElement: StackTraceElement
        get() {
            val stackTrace = Thread.currentThread().stackTrace
            
            var foundOwn = false
            stackTrace.forEach { ste ->
                val isLogger = ste.className == Logger::class.qualifiedName
                if (isLogger) {
                    foundOwn = true
                } else if (foundOwn) {
                    return ste
                }
            }
            
            Log.w("Logger", stackTrace.joinToString("\t\n"))
            throw IllegalStateException("invalid stack")
        }
}
