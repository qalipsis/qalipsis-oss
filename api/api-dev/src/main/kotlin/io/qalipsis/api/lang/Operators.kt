package io.qalipsis.api.lang

/**
 * Executes the block and returns the result if the condition is matched, null otherwise.
 *
 * @see supplyUnless
 * @see coSupplyIf for suspend block
 */
fun <T> supplyIf(condition: Boolean, block: () -> T): T? {
    return if (condition) {
        block()
    } else {
        null
    }
}

/**
 * Executes the block and returns the result if the condition is not matched, null otherwise.
 *
 * @see supplyIf
 * @see coSupplyUnless for suspend block
 */
fun <T> supplyUnless(condition: Boolean, block: () -> T): T? {
    return if (!condition) {
        block()
    } else {
        null
    }
}

/**
 * Executes the block and returns the result if the condition is matched, null otherwise.
 *
 * @see coSupplyUnless
 * @see supplyIf for blocking block
 */
suspend fun <T> coSupplyIf(condition: Boolean, block: suspend () -> T): T? {
    return if (condition) {
        block()
    } else {
        null
    }
}

/**
 * Executes the block and returns the result if the condition is not matched, null otherwise.
 *
 * @see coSupplyIf
 * @see supplyUnless for blocking block
 */
suspend fun <T> coSupplyUnless(condition: Boolean, block: suspend () -> T): T? {
    return if (!condition) {
        block()
    } else {
        null
    }
}

/**
 * Executes the block only if the condition is matched.
 *
 * @see doUnless
 * @see coDoIf for suspend block
 */
fun doIf(condition: Boolean, block: () -> Any?) {
    if (condition) {
        block()
    }
}

/**
 * Executes the block only if the condition is not matched.
 *
 * @see doIf
 * @see coDoUnless for suspend block
 */
fun doUnless(condition: Boolean, block: () -> Any?) {
    if (!condition) {
        block()
    }
}


/**
 * Executes the block only if the condition is matched.
 *
 * @see coDoUnless
 * @see doIf for blocking block
 */
suspend fun coDoIf(condition: Boolean, block: suspend () -> Any?) {
    if (condition) {
        block()
    }
}

/**
 * Executes the block only if the condition is not matched.
 *
 * @see coDoIf
 * @see doUnless for blocking block
 */
suspend fun coDoUnless(condition: Boolean, block: suspend () -> Any?) {
    if (!condition) {
        block()
    }
}
