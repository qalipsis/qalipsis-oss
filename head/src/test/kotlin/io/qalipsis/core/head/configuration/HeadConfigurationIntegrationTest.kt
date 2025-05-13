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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

internal class HeadConfigurationIntegrationTest {

    @MicronautTest(
        environments = [ExecutionEnvironments.HEAD],
        packages = ["io.qalipsis.core.head"],
        startApplication = false
    )
    @Nested
    inner class DefaultConfiguration {

        @Test
        @Timeout(10)
        internal fun `should create the head configuration with the default values`(headConfiguration: HeadConfiguration) {
            assertThat(headConfiguration).all {
                prop(HeadConfiguration::handshakeRequestChannel).isEqualTo("handshake-request")
                prop(HeadConfiguration::unicastChannelPrefix).isEqualTo("unicast-")
                prop(HeadConfiguration::heartbeatChannel).isEqualTo("heartbeat")
                prop(HeadConfiguration::heartbeatDelay).isEqualTo(Duration.ofSeconds(30))
            }
        }
    }


    @PropertySource(
        Property(name = "head.handshake-request-channel", value = "The handshake request channel"),
        Property(name = "head.unicast-channel-prefix", value = "The unicast channel prefix"),
        Property(name = "head.heartbeat-channel", value = "The heartbeat channel"),
        Property(name = "head.heartbeat-delay", value = "PT3M"),
        Property(name = "head.cluster.on-demand-factories", value = "true"),
        Property(name = "head.cluster.zones[0].key", value = "fr"),
        Property(name = "head.cluster.zones[0].title", value = "France"),
        Property(name = "head.cluster.zones[0].description", value = "Western Europe country"),
        Property(name = "head.cluster.zones[0].image", value = "http://images-from-france.fr/logo"),
        Property(name = "head.cluster.zones[1].key", value = "at"),
        Property(name = "head.cluster.zones[1].title", value = "Austria"),
        Property(name = "head.cluster.zones[1].description", value = "Central Europe country"),
        Property(name = "head.cluster.zones[1].image", value = "http://images-from-austria.fr/logo"),
    )
    @MicronautTest(
        environments = [ExecutionEnvironments.HEAD],
        packages = ["io.qalipsis.core.head"],
        startApplication = false
    )
    @Nested
    inner class SpecifiedConfiguration {
        @Test
        @Timeout(10)
        internal fun `should create the head configuration with specified values`(headConfiguration: HeadConfiguration) {
            assertThat(headConfiguration).all {
                prop(HeadConfiguration::handshakeRequestChannel).isEqualTo("The handshake request channel")
                prop(HeadConfiguration::unicastChannelPrefix).isEqualTo("The unicast channel prefix")
                prop(HeadConfiguration::heartbeatChannel).isEqualTo("The heartbeat channel")
                prop(HeadConfiguration::heartbeatDelay).isEqualTo(Duration.ofMinutes(3))
                prop(HeadConfiguration::cluster).all {
                    prop(HeadConfiguration.ClusterConfiguration::onDemandFactories).isTrue()
                }
            }
        }
    }
}
