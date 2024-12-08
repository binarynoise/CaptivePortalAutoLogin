package de.binarynoise.logger

import java.io.File
import java.lang.ref.Reference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass

object Logger {
    
    /**
     * Configuration for the logger
     */
    object Config {
        /**
         * Log to System.out?
         */
        var toSOut: Boolean = true
        
        /**
         * Allow dumping of objects?
         */
        var debugDump: Boolean = false
        
        /**
         * Log to file?
         */
        var toFile: Boolean = false
        
        /**
         * The folder to log to
         */
        var folder: File? = null
        
        /**
         *
         */
        object Include {
            /**
             * Include the location of the caller?
             */
            var location: Boolean = true
            
            /**
             * Include the thread name?
             */
            var threadName: Boolean = false
            
            /**
             * Include the process name?
             */
            var processName: Boolean = false
        }
    }
    
    fun log(message: CharSequence) {
        val callingClassTag = callingClassTag
        log(callingClassTag, message.toString())
        
        platform.log(message.toString())
        
        logToFile(message.toString(), "D", callingClassTag)
    }
    
    fun log(message: CharSequence, t: Throwable) {
        val callingClassTag = callingClassTag
        val stackTraceString = t.stackTraceToString()
        
        logErr(callingClassTag, message.toString())
        logErr(callingClassTag, stackTraceString)
        
        platform.log(message.toString())
        platform.log(t)
        
        logToFile("$message\n$stackTraceString", "E", callingClassTag)
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
        val logFolder = Config.folder ?: error("no log folder set()")
        
        val currentTimeString = currentTimeString
        platform.runInBackground {
            try {
                logString.lines().forEach {
                    val file: File = logFolder.resolve("$currentDateString.log")
                    // TODO clear old logs
                    file.appendText("$currentTimeString $level $callingClassTag: $it\n")
                }
            } catch (e: Exception) {
                platform.printlnErr("failed to log to file " + e.message)
                platform.printlnErr("Disabling logging to file")
                Config.toFile = false
            }
        }
    }
    
    private val currentDateTimeString get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS", Locale.GERMAN).format(Date()).toString()
    private val currentDateString get() = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date()).toString()
    private val currentTimeString get() = SimpleDateFormat("HH:mm:ss,SSS", Locale.GERMAN).format(Date()).toString()
    
    /**
     * Dumps the contents of this object.
     *
     * @param name The name of the object
     * @param forceInclude A set of objects that should be included, even if they would be skipped otherwise
     * @param forceIncludeClasses A set of classes that should be included, even if they would be skipped otherwise
     */
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
        
        if (this == null) {
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
        
        print("(${this::class.java.canonicalNameOrName}@${hashCode()}) -> ")
        
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
                    // println(Arrays.toString(this))
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
            this is Method -> {
                println(
                    this.modifiers.toString() + " " + this.returnType + " " + (this.declaringClass.canonicalNameOrName) + "." + this.name + "(" + this.parameterTypes.joinToString(
                        ", "
                    ) + ")"
                )
            }
            this is Constructor<*> -> {
                println("${this.modifiers} ${this.declaringClass.canonicalNameOrName}(${this.parameterTypes.joinToString(", ")})")
            }
            this is Field -> {
                println("${this.modifiers} ${this.declaringClass.canonicalNameOrName}.${this.type} ${this.name}")
            }
            // skip classes that produce a lot of non-useful output, if not forced
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
            ).any { this::class.java.canonicalNameOrName == it } -> {
                println("i: $this")
            }
            this is Reference<*> -> {
                println()
                get().dump("referenced", nextIndent, processed, forceInclude, forceIncludeClasses)
            }
            this is Class<*> -> {
                println(canonicalNameOrName)
            }
            this is KClass<*> -> {
                println(this.java.canonicalNameOrName)
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
                
                generateSequence<Class<*>>(this::class.java) { it.superclass }.forEach { cls ->
                    fields.addAll(cls.declaredFields.filterNot { Modifier.isStatic(it.modifiers) })
                    methods.addAll(cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 })
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
                methods.removeIf { val methodName = it.name.lowercase().removePrefix("get"); fieldNames.any { methodName.endsWith(it) } }
                
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
    
    private val Class<*>.canonicalNameOrName: String?
        get() = this.canonicalName ?: this.name
    
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
            
            val e = Exception()
            e.stackTrace = stackTrace
            throw IllegalStateException("invalid stack", e)
        }
}
