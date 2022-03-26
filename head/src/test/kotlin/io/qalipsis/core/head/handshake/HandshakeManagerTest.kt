package io.qalipsis.core.head.handshake

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
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
internal class HandshakeManagerTest {

    @RelaxedMockK
    private lateinit var headChannel: HeadChannel

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    private lateinit var factoryService: FactoryService

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @InjectMockKs
    private lateinit var handshakeManager: HandshakeManager

    @BeforeEach
    internal fun setUp() {
        every { headConfiguration.handshakeRequestChannel } returns ""
        every { headConfiguration.unicastChannelPrefix } returns "the-unicast-channel-prefix-"
        every { headConfiguration.heartbeatChannel } returns "the-heartbeat-channel"
        every { headConfiguration.heartbeatDuration } returns Duration.ofSeconds(123)
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
                    relaxedMockk { every { id } returns "scen-1" },
                    relaxedMockk { every { id } returns "scen-2" })
            )
            every { idGenerator.short() } returns "this-id-the-actual-id"
            val latch = Latch(true)
            coEvery { headChannel.publishHandshakeResponse(any(), any()) } coAnswers { latch.release() }

            // when
            handshakeManager.notify(handshakeRequest)
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
                factoryService.register(eq("this-id-the-actual-id"), refEq(handshakeRequest), expectedResponse)
                headChannel.publishHandshakeResponse("reply-channel", expectedResponse)
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
                        relaxedMockk { every { id } returns "scen-1" },
                        relaxedMockk { every { id } returns "scen-2" })
                )

                // when
                handshakeManager.notify(handshakeRequest)

                // then
                val expectedResponse = HandshakeResponse(
                    handshakeNodeId = "a-real-id",
                    nodeId = "a-real-id",
                    unicastChannel = "the-unicast-channel-prefix-a-real-id",
                    heartbeatChannel = "the-heartbeat-channel",
                    heartbeatPeriod = Duration.ofSeconds(123)
                )
                coVerifyOrder {
                    factoryService.register(eq("a-real-id"), refEq(handshakeRequest), expectedResponse)
                    headChannel.publishHandshakeResponse("reply-channel", expectedResponse)
                }

                confirmVerified(factoryService, headChannel)
            }
        }
}