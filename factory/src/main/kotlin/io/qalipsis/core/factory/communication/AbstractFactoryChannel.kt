package io.qalipsis.core.factory.communication

import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.DispatcherChannel

abstract class AbstractFactoryChannel(
    private val directiveRegistry: DirectiveRegistry
) : FactoryChannel {

    override val subscribedHandshakeResponseChannels = mutableSetOf<DispatcherChannel>()

    override val subscribedDirectiveChannels = mutableSetOf<DispatcherChannel>()

    protected lateinit var currentDirectiveBroadcastChannel: DispatcherChannel

    protected lateinit var currentFeedbackChannel: DispatcherChannel

    override suspend fun publishDirective(directive: Directive) {
        val channel = directive.channel.takeIf(String::isNotBlank) ?: currentDirectiveBroadcastChannel
        publishDirective(channel, directiveRegistry.prepareBeforeSend(channel, directive))
    }
}