package io.qalipsis.core.factories.orchestration.directives.consumers

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.factories.StartupFactoryComponent
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.directives.consumers.AbstractDirectiveConsumer
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.inmemory.InMemoryDirectiveProducer
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
) : AbstractDirectiveConsumer(directiveProcessors), StartupFactoryComponent {

    override fun getStartupOrder() = Int.MIN_VALUE

    override fun init() {
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
