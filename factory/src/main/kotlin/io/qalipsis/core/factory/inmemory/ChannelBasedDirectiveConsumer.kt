package io.qalipsis.core.factory.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.AbstractDirectiveConsumer
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.inmemory.InMemoryDirectiveProducer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Implementation of [AbstractDirectiveConsumer] based upon [kotlinx.coroutines.channels.Channel]s, used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [STANDALONE])
internal class ChannelBasedDirectiveConsumer(
    private val producer: InMemoryDirectiveProducer,
    directiveProcessors: Collection<DirectiveProcessor<*>>,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : AbstractDirectiveConsumer(directiveProcessors) {

    override suspend fun start(unicastChannel: String, broadcastChannel: String) {
        coroutineScope.launch {
            log.debug { "Consuming the directives from ${producer.channel}" }
            for (directive in producer.channel) {
                log.trace { "Received directive $directive" }
                process(directive)
            }
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()

    }

}
