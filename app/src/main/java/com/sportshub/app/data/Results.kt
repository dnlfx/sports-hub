package com.sportshub.app.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but lets coroutine cancellation through. Plain `runCatching` turns a
 * cancelled load (the user switched weeks, or left the screen) into a failure, so the old
 * load could still write an error into the screen's state on its way out.
 */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
