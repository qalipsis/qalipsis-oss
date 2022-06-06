package io.qalipsis.core.head.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.Zone
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@ConfigurationProperties("head")
internal interface HeadConfiguration {

    /**
     * Channel to use to register the factory to the head, defaults to "registration".
     */
    @get:NotBlank
    val handshakeRequestChannel: String

    /**
     * Prefix of the channel name to provide to the factories, where they receive the broadcast directives.
     */
    @get:NotBlank
    val unicastChannelPrefix: String

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
     * Set of zones to use to execute the scenarios.
     */
    @get:NotNull
    @get:Bindable(defaultValue = "")
    val zones: Set<Zone>
}