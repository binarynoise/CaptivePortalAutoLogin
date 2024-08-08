package de.binarynoise.logger

internal interface Platform {
    fun <T> print(t: T)
    fun <T> println(t: T)
    
    fun <T> printErr(t: T)
    fun <T> printlnErr(t: T)
    
    fun platformSpecificDump(
        obj: Any,
        name: String,
        nextIndent: Int,
        processed: MutableSet<Any>,
        forceInclude: Set<Any>,
        forceIncludeClasses: Set<Class<*>>,
    ): Boolean
    
    fun runInBackground(block: () -> Unit)
}

internal expect val platform: Platform
