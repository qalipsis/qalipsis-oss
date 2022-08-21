package io.qalipsis.core.head.web.handler

/**
 * Class to transport errors to the clients.
 */
internal data class ErrorResponse(
    val errors: Collection<String>
) {
    constructor(error: String) : this(listOf(error))
}
