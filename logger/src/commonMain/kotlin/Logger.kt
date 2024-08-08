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

object Logger {
    
    object Config {
        var toSOut: Boolean = true
        var debugDump = false
        
        var toFile: Boolean = false
        var folder: File? = null
        
        object Include {
            var location: Boolean = true
            var threadName: Boolean = false
            var processName: Boolean = false
        }
    }
    
    fun log(message: CharSequence) {
        val callingClassTag = callingClassTag
        log(callingClassTag, message.toString())
        
        logToFile(message.toString(), "D", callingClassTag)
    }
    
    fun log(message: CharSequence, t: Throwable) {
        val callingClassTag = callingClassTag
        val stackTraceString = t.stackTraceToString()
        
        logErr(callingClassTag, message.toString())
        logErr(callingClassTag, stackTraceString)
        
        logToFile("" + message + "\n" + stackTraceString, "E", callingClassTag)
    }
    
    internal fun log(callingClassTag: String, message: String) {
        if (!Config.toSOut) return
        message.lines().forEach {
            platform.println("$callingClassTag: $it")
        }
    }
    
    internal fun logErr(callingClassTag: String, message: String) {
        if (!Config.toSOut) return
        message.lines().forEach {
            platform.printlnErr("$callingClassTag: $it")
        }
    }
    
    internal fun logToFile(logString: String, level: String, callingClassTag: String) {
        if (!Config.toFile) return
        val logFolder = Config.folder ?: return
        
        val currentTimeString = currentTimeString
        platform.runInBackground {
            try {
                logString.lines().forEach {
                    val file: File = logFolder.resolve("$currentDateString.log")
                    // TODO clear old logs
                    file.appendText("$currentTimeString $level $callingClassTag: $it\n")
                }
            } catch (e: Exception) {
                log("", e)
            }
        }
    }
    
    private val currentDateTimeString get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS", Locale.GERMAN).format(Date()).toString()
    private val currentDateString get() = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date()).toString()
    private val currentTimeString get() = SimpleDateFormat("HH:mm:ss,SSS", Locale.GERMAN).format(Date()).toString()
    
    fun Any?.dump(name: String, forceInclude: Set<Any> = emptySet(), forceIncludeClasses: Set<Class<*>> = emptySet()) {
        log("dumping $name")
        if (!Config.debugDump) return
        dump(name, 0, mutableSetOf(), forceInclude, forceIncludeClasses)
        System.out.flush()
    }
    
    internal fun Any?.dump(name: String, indent: Int, processed: MutableSet<Any>, forceInclude: Set<Any>, forceIncludeClasses: Set<Class<*>>) {
        //<editor-fold defaultstate="collapsed" desc="...">
        if (!Config.debugDump) return
        
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
            
            forceInclude.none { it == this } && forceIncludeClasses.none { it.isInstance(this) } && listOf(
                "android.app.ActivityManager",
                "android.content.Context",
                "android.content.res.ApkAssets",
                "android.content.res.Resources",
                "android.content.res.ResourcesImpl",
                "android.os.Handler",
                "android.view.accessibility.AccessibilityManager",
                "android.view.View",
                "android.view.View.AttachInfo",
                "androidx.appcompat.app.AppCompatDelegate",
                "androidx.fragment.app.Fragment",
                "j\$.time.LocalDateTime",
                "java.lang.ClassLoader",
                "java.lang.reflect.Member",
                "java.lang.Thread",
                "java.lang.ThreadGroup",
                "kotlin.Function",
                "kotlinx.datetime.LocalDateTime",
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
            platform.platformSpecificDump(this, name, nextIndent, processed, forceInclude, forceIncludeClasses) -> {
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
    
    private val callingClassTag: String
        get() {
            val stackTraceElement = callingClassStackTraceElement
            val simpleClassName = stackTraceElement.simpleClassName
            
            return buildString {
                if (Config.Include.processName) {
                    val fullProcess = ProcessHandle.current().info().command().orElse("")
                    val index = fullProcess.indexOfLast { it == ':' }
                    if (index != -1) {
                        val process = fullProcess.substring(index)
                        append(process)
                    }
                    padEnd(15)
                    append(" ")
                }
                if (Config.Include.threadName) {
                    val thread = Thread.currentThread().name
                    append(thread.padEnd(20))
                    append(" ")
                }
                
                append(simpleClassName.padEnd(35))
                append(" ")
                
                if (Config.Include.location) {
                    val lineNumber = stackTraceElement.lineNumber
                    val file = stackTraceElement.fileName ?: "unknown"
                    append("($file:$lineNumber)".padStart(45))
                }
            }
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
            
            platform.printlnErr(stackTrace.joinToString("\t\n"))
            
            
            throw IllegalStateException("invalid stack")
        }
}
