package de.binarynoise.logger

import java.io.File
import java.lang.ref.Reference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InaccessibleObjectException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
        var debugDump: Boolean = true
        
        /**
         * Log to file?
         */
        var toFile: Boolean = false
        
        /**
         * The folder to log to
         */
        var folder: File? = null
        
        /**
         * The max age of log files to keep
         */
        var folderCleanupDays: Long = 7
        
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
        val logFolder = Config.folder ?: error("no log folder set")
        
        val currentTimeString = currentTimeString
        platform.runInBackground {
            try {
                logString.lines().forEach { line ->
                    val file: File = logFolder.resolve("$currentDateString.log")
                    file.appendText("$currentTimeString $level $callingClassTag: $line\n")
                }
                
                cleanOldLogs()
            } catch (e: Exception) {
                platform.printlnErr("failed to log to file " + e.message)
                platform.printlnErr("Disabling logging to file")
                Config.toFile = false
            }
        }
    }
    
    internal fun cleanOldLogs() {
        val logFolder = Config.folder ?: return
        val maxAge = Config.folderCleanupDays
        platform.runInBackground {
            try {
                val oldestDateString = LocalDate.now().minusDays(maxAge).format(dateFormatter)
                logFolder.list { _, name -> name.substringBeforeLast(".") < oldestDateString }.orEmpty().forEach {
                    logFolder.resolve(it).delete()
                    log(callingClassTag, it.toString())
                }
            } catch (e: Exception) {
                val callingClassTag = callingClassTag
                logErr(callingClassTag, "failed to delete old logs")
                logErr(callingClassTag, e.stackTraceToString())
            }
        }
    }
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.GERMAN)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss,SSS", Locale.GERMAN)
    private val currentDateString: String get() = LocalDate.now().format(dateFormatter)
    private val currentTimeString: String get() = LocalTime.now().format(timeFormatter)
    
    /**
     * Dumps the contents of this object.
     *
     * @param name The name of the object
     * @param forceInclude A set of objects that should be included, even if they would be skipped otherwise
     * @param forceIncludeClasses A set of classes that should be included, even if they would be skipped otherwise
     */
    fun Any?.dump(name: String, forceInclude: Set<Any> = emptySet(), forceIncludeClasses: Set<Class<*>> = emptySet()) {
        if (!Config.debugDump) return
        log("dumping $name")
        dump(name, 0, mutableSetOf(), forceInclude, forceIncludeClasses)
        System.out.flush()
    }
    
    internal fun Any?.dump(
        name: String,
        indent: Int,
        processed: MutableSet<Any>,
        forceInclude: Set<Any>,
        forceIncludeClasses: Set<Class<*>>,
    ) {
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
                        this.forEachIndexed { index, value ->
                            value.dump(
                                index.toString(), nextIndent, processed, forceInclude, forceIncludeClasses
                            )
                        }
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
                    this.forEachIndexed { index, value ->
                        value.dump(
                            index.toString(), nextIndent, processed, forceInclude, forceIncludeClasses
                        )
                    }
                }
            }
            this is Map<*, *> -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEach { (k, v) ->
                        v.dump(
                            k.toString(), nextIndent, processed, forceInclude, forceIncludeClasses
                        )
                    }
                }
            }
            this is Method -> {
                println(
                    "${this.modifiers} ${this.returnType} ${this.declaringClass.canonicalNameOrName}.${this.name}(${
                        this.parameterTypes.joinToString(
                            ", "
                        )
                    })"
                )
            }
            this is Constructor<*> -> {
                println(
                    "${this.modifiers} ${this.declaringClass.canonicalNameOrName}(${
                        this.parameterTypes.joinToString(
                            ", "
                        )
                    })"
                )
            }
            this is Field -> {
                println("${this.modifiers} ${this.declaringClass.canonicalNameOrName}.${this.type} ${this.name}")
            }
            this is Enum<*> -> {
                println("${this::class.java.canonicalNameOrName}.${this.name}")
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
                "j$.time.LocalDateTime",
                "java.lang.ClassLoader",
                "java.lang.reflect.Member",
                "java.lang.Thread",
                "java.lang.ThreadGroup",
                "kotlin.Function",
                "kotlinx.datetime.LocalDateTime",
                "kotlinx.coroutines.SupervisorJobImpl",
                "io.ktor.server.application.Application",
                "io.netty.channel.nio.NioEventLoop",
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
            this::class.java.declaredFields.any { it.name.equals("INSTANCE", true) } -> {
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
                
                generateSequence(this::class.java) { it.superclass }.forEach { cls ->
                    fields.addAll(cls.declaredFields.filterNot { Modifier.isStatic(it.modifiers) })
                    methods.addAll(cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 })
                }
                
                fields.sortedBy { it.name }.forEach {
                    try {
                        it.isAccessible = true
                        it.get(this).dump(it.name, nextIndent, processed, forceInclude, forceIncludeClasses)
                    } catch (e: ReflectiveOperationException) {
                        println("failed to dump field ${it.name}")
                        when (e.suppressed.size) {
                            e.suppressed.size -> println(e.message)
                            e.suppressed.size -> println(e.suppressed[0].message)
                            else -> e.printStackTrace()
                        }
                    } catch (_: InaccessibleObjectException) {
                        println("failed to make field ${it.name} accessible")
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
