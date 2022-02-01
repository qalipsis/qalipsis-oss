package io.qalipsis.core.directives

/**
 * Registry responsible for hosting and publishing the persisted directives.
 *
 * @author Eric Jessé
 */
interface DirectiveRegistry {

    /**
     * Persist a [Directive] into the registry.
     */
    suspend fun save(key: DirectiveKey, directive: Directive)

    /**
     * Keeps a [Directive] into the registry for later use with [get] and [remove].
     */
    suspend fun keep(directive: Directive)

    /**
     * Pop first value from a [QueueDirective].
     */
    suspend fun <T> pop(reference: QueueDirectiveReference<T>): T?

    /**
     * Return the full set of records from a [ListDirective].
     */
    suspend fun <T> list(reference: ListDirectiveReference<T>): List<T>

    /**
     * Deletes and returns the value of the [SingleUseDirective] if it was not yet read.
     */
    suspend fun <T> read(reference: SingleUseDirectiveReference<T>): T?

    /**
     * Gets a non reference directive that was previously saved.
     */
    suspend fun get(key: DirectiveKey): Directive?

    /**
     * Removes a non reference directive that was previously saved.
     */
    suspend fun remove(key: DirectiveKey)
}
