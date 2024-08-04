package de.binarynoise.liberator

import de.binarynoise.logger.Logger.log

/**
 * Casts the object to T.
 * If T is nullable, a safe cast is performed.
 */
@Suppress("UNCHECKED_CAST")
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

inline fun <T> tryOrNull(block: () -> T): T? {
    try {
        return block()
    } catch (e: Exception) {
        log("exception in tryOrNull", e)
        return null
    }
}

inline fun <T> tryOrIgnore(block: () -> T): Unit {
    try {
        block()
    } catch (e: Exception) {
        log("exception in tryOrIgnore", e)
    }
}
