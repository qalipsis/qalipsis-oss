/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.inmemory

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.campaign.ChannelNameFactory
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class StandaloneHandshakeManagerTest {

    @RelaxedMockK
    private lateinit var headChannel: HeadChannel

    @RelaxedMockK
    private lateinit var factoryService: FactoryService

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @RelaxedMockK
    private lateinit var channelNameFactory: ChannelNameFactory

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var handshakeManager: StandaloneHandshakeManager

    @BeforeEach
    internal fun setUp() {
        every { headConfiguration.handshakeRequestChannel } returns ""
        every { headConfiguration.unicastChannelPrefix } returns "the-unicast-channel-prefix-"
        every { headConfiguration.heartbeatChannel } returns "the-heartbeat-channel"
        every { headConfiguration.heartbeatDelay } returns Duration.ofSeconds(123)
    }

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    internal fun `should respond to the handshake with a real ID and trigger the campaign auto-starter when present`() =
        testCoroutineDispatcher.runTest {
            // given
            val handshakeRequest = HandshakeRequest(
                nodeId = "_temporary-node-id",
                tags = mapOf("key1" to "value1", "key2" to "value2"),
                replyTo = "reply-channel",
                scenarios = listOf(
                    relaxedMockk { every { name } returns "scen-1" },
                    relaxedMockk { every { name } returns "scen-2" }),
                tenant = "my-tenant"
            )
            coEvery {
                channelNameFactory.getUnicastChannelName(any())
            } returns "this-id-the-actual-id"
            val latch = Latch(true)
            coEvery {
                headChannel.publishHandshakeResponse(
                    channelName = any(),
                    handshakeResponse = any()
                )
            } coAnswers { latch.release() }

            // when
            handshakeManager.notify(handshakeRequest = handshakeRequest)
            latch.await()

            // then
            val expectedResponse = HandshakeResponse(
                handshakeNodeId = "_temporary-node-id",
                nodeId = "this-id-the-actual-id",
                unicastChannel = "the-unicast-channel-prefix-this-id-the-actual-id",
                heartbeatChannel = "the-heartbeat-channel",
                heartbeatPeriod = Duration.ofSeconds(123)
            )
            coVerifyOrder {
                channelNameFactory.getUnicastChannelName(refEq(handshakeRequest))
                factoryService.register(
                    actualNodeId = eq("this-id-the-actual-id"),
                    handshakeRequest = refEq(handshakeRequest),
                    handshakeResponse = expectedResponse
                )
                headChannel.publishHandshakeResponse(
                    channelName = "reply-channel",
                    handshakeResponse = expectedResponse
                )
            }

            confirmVerified(factoryService, headChannel)
        }

    @Test
    internal fun `should respond to the handshake not not trigger the campaign auto-starter when absent`() =
        testCoroutineDispatcher.runTest {
            testCoroutineDispatcher.runTest {
                // given
                val handshakeRequest = HandshakeRequest(
                    nodeId = "a-real-id",
                    tags = mapOf("key1" to "value1", "key2" to "value2"),
                    replyTo = "reply-channel",
                    scenarios = listOf(
                        relaxedMockk { every { name } returns "scen-1" },
                        relaxedMockk { every { name } returns "scen-2" }),
                    tenant = "my-tenant",
                    zone = "en"
                )
                coEvery { channelNameFactory.getUnicastChannelName(any()) } returns "a-real-id"

                // when
                handshakeManager.notify(handshakeRequest = handshakeRequest)

                // then
                val expectedResponse = HandshakeResponse(
                    handshakeNodeId = "a-real-id",
                    nodeId = "a-real-id",
                    unicastChannel = "the-unicast-channel-prefix-a-real-id",
                    heartbeatChannel = "the-heartbeat-channel",
                    heartbeatPeriod = Duration.ofSeconds(123)
                )
                coVerifyOrder {
                    channelNameFactory.getUnicastChannelName(refEq(handshakeRequest))
                    factoryService.register(
                        actualNodeId = eq("a-real-id"),
                        handshakeRequest = refEq(handshakeRequest),
                        handshakeResponse = expectedResponse
                    )
                    headChannel.publishHandshakeResponse(
                        channelName = "reply-channel",
                        handshakeResponse = expectedResponse
                    )
                }

                confirmVerified(factoryService, headChannel)
            }
        }
}