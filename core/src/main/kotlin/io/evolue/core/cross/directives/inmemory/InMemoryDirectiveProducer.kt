package io.evolue.core.cross.directives.inmemory

import io.evolue.core.annotations.LogInput
import io.evolue.core.cross.configuration.ENV_STANDALONE
import io.evolue.api.orchestration.directives.Directive
import io.evolue.api.orchestration.directives.DirectiveProducer
import io.evolue.api.orchestration.directives.DirectiveRegistry
import io.evolue.api.orchestration.directives.ReferencableDirective
import io.micronaut.context.annotation.Requires
import kotlinx.coroutines.channels.Channel
import org.slf4j.event.Level
import javax.inject.Singleton

/**
 * Implementation of [DirectiveProducer] publishing the [Directive]s via a [Channel], used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_STANDALONE])
internal class InMemoryDirectiveProducer(
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
