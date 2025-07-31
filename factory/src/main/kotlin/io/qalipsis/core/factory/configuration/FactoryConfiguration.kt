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

package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import io.qalipsis.api.constraints.PositiveDuration
import java.time.Duration
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive

@ConfigurationProperties("factory")
class FactoryConfiguration {

    /**
     * ID of this factory, defaults to a random generated value which will be replaced after the registration
     * to the head.
     */
    var nodeId: String = ""

    /**
     * Set of tags assigned to this factory, defaults to empty.
     */
    var tags: Map<String, String> = emptyMap()

    /**
     * Folder where the metadata of the factory should be saved for later use.
     */
    @field:NotEmpty
    var metadataPath: String = "./metadata"

    /**
     * Identifier of the tenant owning the factory, defaults to "_qalipsis_ten_", only change with care.
     */
    var tenant: String = "_qalipsis_ten_"

    /**
     * Configuration of the handshake operations.
     */
    var handshake: HandshakeConfiguration = HandshakeConfiguration()

    /**
     * This object contains the settings applied to each key and value stored in cache.
     */
    var cache: CacheConfiguration = CacheConfiguration()

    /**
     * Configuration of the operations of assignment of minions to the factory.
     */
    var assignment = AssignmentConfiguration()

    /**
     * This object contains the values to validate the campaign.
     */
    var campaign = CampaignConfiguration()

    /**
     * Configuration of a key of a Zone of the factory declared in the head.
     */
    var zone: String? = null

    override fun toString(): String {
        return "FactoryConfiguration(nodeId='$nodeId', tags=$tags, metadataPath='$metadataPath', tenant='$tenant', handshake=$handshake, cache=$cache, assignment=$assignment, campaign=$campaign, zoneKey=$zone)"
    }

    /**
     * Directive registry configuration for a factory.
     */
    @ConfigurationProperties("handshake")
    class HandshakeConfiguration {

        /**
         * Channel to use to register the factory to the head, defaults to "registration".
         */
        @field:NotEmpty
        var requestChannel: String = "handshake-request"

        /**
         * Channel to use to register the factory to the head, defaults to "registration".
         */
        @field:NotEmpty
        var responseChannel: String = "handshake-response"

        /**
         * Maximal delay to wait for a positive handshake response..
         */
        @field:PositiveDuration
        var timeout: Duration = Duration.ofSeconds(30)

        override fun toString(): String {
            return "HandshakeConfiguration(requestChannel='$requestChannel', responseChannel='$responseChannel', timeout=$timeout)"
        }

    }

    @ConfigurationProperties("cache")
    class CacheConfiguration {

        /**
         * Time to live used for each value in cache.
         */
        @field:PositiveDuration
        var ttl: Duration = Duration.ofMinutes(1)

        /**
         * The prefix of the keys in cache.
         */
        var keyPrefix: String = "shared-state-registry"

        override fun toString(): String {
            return "CacheConfiguration(ttl=$ttl, keyPrefix='$keyPrefix')"
        }

    }

    @ConfigurationProperties("assignment")
    class AssignmentConfiguration {

        /**
         * Size of the evaluation batches of minions to assign.
         */
        @field:Positive
        var evaluationBatchSize: Int = 10

        /**
         * Size of the evaluation batches of minions to assign.
         */
        @field:PositiveDuration
        var timeout: Duration = Duration.ofSeconds(10)

        override fun toString(): String {
            return "AssignmentConfiguration(evaluationBatchSize=$evaluationBatchSize, timeout=$timeout)"
        }

    }

    @ConfigurationProperties("campaign.configuration")
    class CampaignConfiguration {

        /**
         * The maximal number of step specifications in a step scenario.
         */
        @field:Positive
        var maxScenarioStepSpecificationsCount: Int = 100

        override fun toString(): String {
            return "CampaignConfiguration(maxScenarioStepSpecificationsCount=$maxScenarioStepSpecificationsCount)"
        }
    }

    companion object {

        /**
         * Name of the file containing the node ID when persisted locally.
         */
        const val NODE_ID_FILE_NAME = "id.data"

    }
}
