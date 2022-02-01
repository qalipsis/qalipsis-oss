package io.qalipsis.core.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.ReferencableDirective
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.Channel
import org.slf4j.event.Level
import javax.annotation.PreDestroy

/**
 * Implementation of [DirectiveProducer] publishing the [Directive]s via a [Channel], used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [STANDALONE])
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

    @PreDestroy
    fun close() {
        kotlin.runCatching {
            channel.cancel()
        }
    }
}
