package io.qalipsis.runtime.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.HandshakeResponseListener
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.communication.HeartbeatListener
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class Listeners(
    val directiveListeners: Collection<DirectiveListener<*>>,
    val handshakeResponseListeners: Collection<HandshakeResponseListener>,
    val feedbackListeners: Collection<FeedbackListener<*>>,
    val handshakeRequestListeners: Collection<HandshakeRequestListener>,
    val heartbeatListeners: Collection<HeartbeatListener>
)