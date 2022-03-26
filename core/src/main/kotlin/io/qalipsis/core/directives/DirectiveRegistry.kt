package io.qalipsis.core.directives

/**
 * Registry responsible for hosting and publishing the persisted directives.
 *
 * @author Eric Jessé
 */
interface DirectiveRegistry {

    suspend fun prepareBeforeSend(channel: DispatcherChannel, directive: Directive): Directive {
        return if (directive is SingleUseDirective<*>) {
            save(channel, directive)
        } else {
            directive
        }
    }

    suspend fun prepareAfterReceived(directive: Directive): Directive? {
        return if (directive is SingleUseDirectiveReference) {
            get(directive)
        } else {
            directive
        }
    }

    /**
     * Persist a [Directive] into the registry.
     */
    suspend fun save(channel: DispatcherChannel, directive: SingleUseDirective<*>): SingleUseDirectiveReference

    /**
     * Fetches and deletes the value of the [SingleUseDirective] if it was not yet read.
     */
    suspend fun <T : SingleUseDirectiveReference> get(reference: T): Directive?

}
