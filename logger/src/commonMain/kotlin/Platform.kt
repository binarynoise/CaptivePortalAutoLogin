package de.binarynoise.logger


/**
 * The Platform interface defines the methods used by the logger to print stuff.
 *
 * This interface is implemented by the platform specific implementations.
 */
internal interface Platform {
    fun <T> print(t: T)
    fun <T> println(t: T)
    
    fun <T> printErr(t: T)
    fun <T> printlnErr(t: T)
    
    /**
     * Ask the platform implementation to handle the object dump.
     *
     * @return true if the object was handled
     */
    fun platformSpecificDump(
        obj: Any,
        name: String,
        nextIndent: Int,
        processed: MutableSet<Any>,
        forceInclude: Set<Any>,
        forceIncludeClasses: Set<Class<*>>,
    ): Boolean
    
    /**
     * Run the given block in the background.
     *
     * @param block The block to execute.
     */
    fun runInBackground(block: () -> Unit)
}

/**
 * The instance of the platform implementation used by the logger.
 */
internal expect val platform: Platform
