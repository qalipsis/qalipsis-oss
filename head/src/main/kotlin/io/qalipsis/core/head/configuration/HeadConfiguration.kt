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
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.core.configuration.ExecutionEnvironments
import java.time.Duration
import javax.validation.constraints.NotBlank

@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@ConfigurationProperties("head")
class HeadConfiguration {

    /**
     * Channel to use to register the factory to the head, defaults to "registration".
     */
    @field:NotBlank
    var handshakeRequestChannel: String = "handshake-request"

    /**
     * Prefix of the channel name to provide to the factories, where they receive the broadcast directives.
     */
    @field:NotBlank
    var unicastChannelPrefix: String = "unicast"

    /**
     * Channel to send the heartbeats to.
     */
    @field:NotBlank
    var heartbeatChannel: String = "heartbeat"

    /**
     * Duration of the heartbeat to emit from the factories.
     */
    @field:PositiveDuration
    var heartbeatDelay: Duration = Duration.ofSeconds(30)

    /**
     * Duration to wait for feedbacks before forcing the complete end of a campaign.
     */
    @field:PositiveDuration
    var campaignCancellationStateGracePeriod: Duration = Duration.ofSeconds(8)

    /**
     * Contains configuration properties (zones and factories) for clusters.
     */
    var cluster: ClusterConfiguration = ClusterConfiguration()

    @ConfigurationProperties("cluster")
    class ClusterConfiguration {

        /**
         * When the flag is set to true, the factories are started for a unique campaign and stopped
         * once it completes. The head should provide the convenient feedbacks to orchestrate them.
         */
        var onDemandFactories: Boolean = false
    }
}
