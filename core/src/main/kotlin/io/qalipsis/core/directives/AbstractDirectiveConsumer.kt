package io.qalipsis.core.directives

import io.qalipsis.api.logging.LoggerHelper.logger

/**
 * An [AbstractDirectiveConsumer] is responsible for consuming the messages with the [Directive]s coming from the head
 * and let them process.
 *
 * Different implementation can be used depending on the deployment mode of qalipsis: standalone or distributed.
 *
 * @author Eric Jessé
 */
abstract class AbstractDirectiveConsumer(
    directiveProcessors: Collection<DirectiveProcessor<*>>
) : DirectiveConsumer {

    private val orderedDirectiveProcessors = directiveProcessors.sortedBy { it.order() }

    /**
     * Pass the directive to all the known processors supporting it.
     */
    @Suppress("UNCHECKED_CAST")
    protected suspend fun process(directive: Directive) {
        orderedDirectiveProcessors.filter { it.accept(directive) }
            .map { it as DirectiveProcessor<Directive> }
            .forEach { processor ->
                log.trace { "Processing the directive $directive with the processor $processor" }
                try {
                    processor.process(directive)
                } catch (e: Exception) {
                    log.error(e) { "An error occurred while processing the directive $directive with the processor $processor: ${e.message}" }
                }
            }
    }

    private companion object {

        @JvmStatic
        val log = logger()

    }
}
