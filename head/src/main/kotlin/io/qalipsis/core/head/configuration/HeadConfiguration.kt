/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.Zone
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

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
    @get:PositiveDuration
    @get:Bindable(defaultValue = "PT30S")
    val heartbeatDelay: Duration

    /**
     * Contains configuration properties (zones and factories) for clusters.
     */
    val cluster: ClusterConfiguration

    @ConfigurationProperties("cluster")
    interface ClusterConfiguration {

        /**
         * Set of zones to use to execute the scenarios.
         */
        @get:NotNull
        @get:Bindable(defaultValue = "")
        val zones: Set<Zone>

        /**
         * When the flag is set to true, the factories are started for a unique campaign and stopped
         * once it completes. The head should provide the convenient feedbacks to orchestrate them.
         */
        @get:Bindable(defaultValue = "false")
        val onDemandFactories: Boolean

    }
}