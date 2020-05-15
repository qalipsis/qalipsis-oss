package io.evolue.core.factory.orchestration.directives.consumers

import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/**
 * Implementation of [DirectiveConsumer] based upon [kotlinx.coroutines.channels.Channel]s, used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
internal class ChannelBasedDirectiveConsumer(
    private val channel: ReceiveChannel<Directive>,
    directiveProcessors: Collection<DirectiveProcessor<*>>
) : DirectiveConsumer(directiveProcessors) {

    init {
        GlobalScope.launch {
            for (directive in channel) {
                process(directive)
            }
        }
    }

}