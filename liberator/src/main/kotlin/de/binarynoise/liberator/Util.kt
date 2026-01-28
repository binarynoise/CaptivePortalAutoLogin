package de.binarynoise.liberator

import de.binarynoise.logger.Logger.log
import org.json.JSONArray

// TODO: move somewhere else

/**
 * Casts the object to T.
 * If T is nullable, a safe cast is performed.
 */
inline fun <reified T> Any.cast(): T = if (null is T) {
    // T is nullable.
    // Use safe cast.
    // Casting as T again is needed because compiler doesn't know in advance that T is nullable and complains otherwise.
    (this as? T) as T
} else {
    // T is not nullable. Use direct cast.
    this as T
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
 * Executes the given block and returns its result.
 * If an exception is thrown, returns given default instead.
 *
 * @param default the value to return if an exception was thrown
 * @param block The block to execute.
 * @return The result of the block or default if an exception was thrown.
 */
inline fun <T> tryOrDefault(default: T, block: () -> T): T {
    try {
        return block()
    } catch (_: Exception) {
        return default
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

/**
 * Returns an iterable view of this JSONArray that can be used on Android
 * as the Android implementation of JSONArray does not implement Iterable.
 */
@Suppress("USELESS_IS_CHECK") // instance check not useless on Android
// TODO: create r8 bug report: r8 removes the instance check even if told not to optimize
fun JSONArray.asIterable(): Iterable<Any> = /*if (this is Iterable<Any>) this else*/ object : Iterable<Any> {
    override fun iterator(): Iterator<Any> = object : Iterator<Any> {
        private var index = 0
        override fun hasNext(): Boolean = index < length()
        override fun next(): Any = get(index++)
    }
}

class NoSuccessException(message: String) : Exception(message)

fun <T> Sequence<Result<T>>.firstSuccess(): Result<T> {
    val exceptions = mutableListOf<Throwable>()
    for (result in this) {
        result.onSuccess { return result }
        result.onFailure { exceptions.add(it) }
    }
    val wrapper = NoSuccessException("no success: " + exceptions.joinToString(", ") { it.message.toString() })
    for (exception in exceptions) {
        wrapper.addSuppressed(exception)
    }
    return Result.failure(wrapper)
}

fun <T> List<Result<T>>.successes(): Result<List<T>> {
    val exceptions = mutableListOf<Throwable>()
    val successes = mutableListOf<T>()
    for (result in this) {
        result.onSuccess { successes.add(it) }
        result.onFailure { exceptions.add(it) }
    }
    if (successes.isNotEmpty()) {
        return Result.success(successes)
    }
    
    val wrapper = NoSuccessException("no success: " + exceptions.joinToString(", ") { it.message.toString() })
    for (exception in exceptions) {
        wrapper.addSuppressed(exception)
    }
    return Result.failure(wrapper)
}
