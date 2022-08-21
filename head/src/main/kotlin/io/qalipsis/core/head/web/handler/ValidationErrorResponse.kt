package io.qalipsis.core.head.web.handler

/**
 * Class to transport validation errors to the clients.
 */
internal data class ValidationErrorResponse(
    val errors: Collection<ValidationError>
) {
    constructor(error: ValidationError) : this(listOf(error))

    data class ValidationError(
        val property: String,
        val message: String
    )
}
