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

package io.qalipsis.core.factory.init

import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.justRun
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.CommunicationChannelConfiguration
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class ClusterInitializationContextTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    @RelaxedMockK
    private lateinit var communicationChannelConfiguration: CommunicationChannelConfiguration

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var handshakeBlocker: HandshakeBlocker

    @InjectMockKs
    @SpyK(recordPrivateCalls = true)
    private lateinit var initializationContext: ClusterInitializationContext

    @Test
    internal fun `should configure the factory after the handshake and release the handshake blocker`() =
        testDispatcherProvider.run {
            // given
            every { factoryConfiguration.nodeId } returns "the-node-id"
            every { factoryConfiguration.handshake.responseChannel } returns "the-response-channel"
            val handshakeResponse = HandshakeResponse(
                handshakeNodeId = "the-node-id",
                nodeId = "the-actual-node-id",
                unicastChannel = "the-actual-channel",
                heartbeatChannel = "the-heartbeat-channel",
                heartbeatPeriod = Duration.ofMinutes(1)
            )
            justRun { initializationContext["persistNodeIdIfDifferent"]("the-actual-node-id") }

            // when
            initializationContext.notify(handshakeResponse)

            // then
            coVerifyOrder {
                initializationContext["persistNodeIdIfDifferent"]("the-actual-node-id")
                factoryConfiguration setProperty "nodeId" value "the-actual-node-id"
                communicationChannelConfiguration setProperty "unicastChannel" value "the-actual-channel"
                factoryChannel.subscribeDirective("the-actual-channel")
                factoryChannel.unsubscribeHandshakeResponse("the-response-channel")
                handshakeBlocker.notifySuccessfulRegistration()
            }
        }
}