package io.qalipsis.core.factories.orchestration.directives.consumers

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.directives.consumers.AbstractDirectiveConsumer
import io.qalipsis.core.cross.configuration.ENV_STANDALONE
import io.qalipsis.core.cross.directives.inmemory.InMemoryDirectiveProducer
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
