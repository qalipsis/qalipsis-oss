package io.qalipsis.core.head.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

@ConfigurationProperties("head")
internal interface HeadConfiguration {

    /**
     * Channel to use to register the factory to the head, defaults to "registration".
     */
    @get:NotBlank
    val handshakeRequestChannel: String

    /**
     * Channel to use to register the factory to the head, defaults to "registration".
     */
    @get:NotBlank
    val handshakeResponseChannel: String

    /**
     * Prefix of the channel name to provide to the factories, where they receive the broadcast directives.
     */
    @get:NotBlank
    val unicastChannelPrefix: String

    /**
     * Channel name to provide to the factories, where they receive the broadcast directives.
     */
    @get:NotBlank
    val broadcastChannel: String

    /**
     * Channel to send the heartbeats to.
     */
    @get:NotBlank
    val heartbeatChannel: String

    /**
     * Duration of the heartbeat to emit from the factories.
     */
    @get:Positive
    val heartbeatDuration: Duration

    /**
     * Channel to send the heartbeats to.
     */
    @get:NotBlank
    val feedbackChannel: String

}