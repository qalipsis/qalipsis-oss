package io.evolue.core.cross.driving.directives

/**
 * Registry responsible for hosting and publishing the persisted directives.
 *
 * @author Eric Jessé
 */
internal interface DirectiveRegistry {

    /**
     * Persist a [Directive] into the registry.
     */
    suspend fun save(key: DirectiveKey, directive: Directive)

    /**
     * Pop first value from a [QueueDirective].
     */
    suspend fun <T> pop(reference: QueueDirectiveReference<T>): T?

    /**
     * Return the full set of records from a [ListDirective].
     */
    suspend fun <T> list(reference: ListDirectiveReference<T>): List<T>

    /**
     * Delete and returns the value of the [SingleUseDirective] if it was not yet read.
     */
    suspend fun <T> read(reference: SingleUseDirectiveReference<T>): T?
}
