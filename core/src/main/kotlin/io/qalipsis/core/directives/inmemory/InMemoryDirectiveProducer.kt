package io.qalipsis.core.directives.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.directives.ReferencableDirective
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments.ENV_STANDALONE
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.Channel
import org.slf4j.event.Level

/**
 * Implementation of [DirectiveProducer] publishing the [Directive]s via a [Channel], used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_STANDALONE])
class InMemoryDirectiveProducer(
    private val registry: DirectiveRegistry
) : DirectiveProducer {

    val channel = Channel<Directive>(Channel.BUFFERED)

    @LogInput(level = Level.DEBUG)
    override suspend fun publish(directive: Directive) {
        when (directive) {
            is ReferencableDirective<*> -> {
                val ref = directive.toReference()
                registry.save(ref.key, directive)
                channel.send(ref)
            }
            else -> {
                channel.send(directive)
            }
        }
    }

}
