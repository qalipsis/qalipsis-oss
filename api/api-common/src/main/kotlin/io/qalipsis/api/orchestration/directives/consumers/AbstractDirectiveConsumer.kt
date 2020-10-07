package io.qalipsis.api.orchestration.directives.consumers

import io.qalipsis.api.factories.StartupFactoryComponent
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor

/**
 * A [AbstractDirectiveConsumer] is responsible for consuming the messages with the [Directive]s coming from the head
 * and let them process.
 *
 * Different implementation can be used depending on the deployment mode of qalipsis: standalone or distributed.
 *
 * @author Eric Jessé
 */
 abstract class AbstractDirectiveConsumer(
    directiveProcessors: Collection<DirectiveProcessor<*>>
) : StartupFactoryComponent {

    private val orderedDirectiveProcessors = directiveProcessors.sortedBy { it.order() }

    /**
     * Pass the directive to all the known processors supporting it.
     */
    @Suppress("UNCHECKED_CAST")
    protected suspend fun process(directive: Directive) {
        orderedDirectiveProcessors.filter { it.accept(directive) }
            .map { it as DirectiveProcessor<Directive> }
            .forEach {
                it.process(directive)
            }
    }
}
