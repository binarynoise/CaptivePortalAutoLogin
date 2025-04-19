package de.binarynoise.liberator

import de.binarynoise.logger.Logger.log

// TODO: move somewhere else

/**
 * Casts the object to T.
 * If T is nullable, a safe cast is performed.
 */
inline fun <reified T> Any.cast(): T {
    return when {
        null is T -> {
            // T is nullable. Use safe cast.
            // Casting as T again is needed because compiler doesn't know in advance that T is nullable and complains otherwise.
            (this as? T) as T
        }
        else -> {
            // T is not nullable. Use direct cast.
            this as T
        }
    }
}

/**
 * Executes the given block and returns its result.
 * If an exception is thrown, returns null instead.
 *
 * @param block The block to execute.
 * @return The result of the block or null if an exception was thrown.
 */
inline fun <T> tryOrNull(block: () -> T): T? {
    try {
        return block()
    } catch (_: Exception) {
        return null
    }
}

/**
 * Executes the given block and ignores any exceptions thrown.
 *
 * @param block The block to execute.
 */
inline fun <T> tryOrIgnore(block: () -> T) {
    try {
        block()
    } catch (_: Exception) {
    }
}

/**
 * Executes the given block and logs any exceptions thrown.
 *
 * @param block The block to execute.
 */
inline fun tryOrLog(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        log("exception in tryOrLog", e)
    }
}
