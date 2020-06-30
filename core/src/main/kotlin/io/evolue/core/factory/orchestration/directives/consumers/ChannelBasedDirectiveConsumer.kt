package io.evolue.core.factory.orchestration.directives.consumers

import io.evolue.core.cross.configuration.ENV_STANDALONE
import io.evolue.core.cross.driving.directives.inmemory.InMemoryDirectiveProducer
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import io.micronaut.context.annotation.Requires
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.annotation.PostConstruct
import javax.inject.Singleton

/**
 * Implementation of [AbstractDirectiveConsumer] based upon [kotlinx.coroutines.channels.Channel]s, used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_STANDALONE])
internal class ChannelBasedDirectiveConsumer(
    private val producer: InMemoryDirectiveProducer,
    directiveProcessors: Collection<DirectiveProcessor<*>>
) : AbstractDirectiveConsumer(directiveProcessors) {

    @PostConstruct
    fun init() {
        GlobalScope.launch {
            for (directive in producer.channel) {
                process(directive)
            }
        }
    }

}
