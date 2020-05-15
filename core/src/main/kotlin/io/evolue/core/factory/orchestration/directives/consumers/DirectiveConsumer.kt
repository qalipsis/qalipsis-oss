package io.evolue.core.factory.orchestration.directives.consumers

import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor

/**
 * A [DirectiveConsumer] is responsible for consuming the messages with the [Directive]s coming from the head
 * and let them process.
 *
 * Different implementation can be used depending on the deployment mode of evolue: standalone or distributed.
 *
 * @author Eric Jessé
 */
internal abstract class DirectiveConsumer(
    private val directiveProcessors: Collection<DirectiveProcessor<*>>
) {

    /**
     * Pass the directive to all the known processors supporting it.
     */
    protected suspend fun process(directive: Directive) {
        directiveProcessors.filter { it.accept(directive) }
            .map { it as DirectiveProcessor<Directive> }
            .forEach {
                it.process(directive)
            }
    }
}