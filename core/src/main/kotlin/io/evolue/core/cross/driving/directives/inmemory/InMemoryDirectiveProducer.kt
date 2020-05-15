package io.evolue.core.cross.driving.directives.inmemory

import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveProducer
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.core.cross.driving.directives.ReferencableDirective
import kotlinx.coroutines.channels.Channel

/**
 * Implementation of [DirectiveProducer] publishing the [Directive]s via a [Channel], used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
internal class InMemoryDirectiveProducer(
    private val registry: DirectiveRegistry
) : DirectiveProducer {

    val channel = Channel<Directive>(Channel.BUFFERED)

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