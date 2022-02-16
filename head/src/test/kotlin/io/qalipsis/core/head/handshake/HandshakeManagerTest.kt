package io.qalipsis.core.head.handshake

import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.micronaut.context.env.Environment
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.handshake.HandshakeHeadChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.campaign.CampaignAutoStarter
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.launch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.Optional

@WithMockk
internal class HandshakeManagerTest {

    @RelaxedMockK
    private lateinit var environment: Environment

    @RelaxedMockK
    private lateinit var handshakeHeadChannel: HandshakeHeadChannel

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    private lateinit var campaignAutoStarter: CampaignAutoStarter

    @RelaxedMockK
    private lateinit var factoryService: FactoryService

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @BeforeEach
    internal fun setUp() {
        every { headConfiguration.handshakeRequestChannel } returns ""
        every { headConfiguration.handshakeResponseChannel } returns ""
        every { headConfiguration.unicastChannelPrefix } returns "the-unicast-channel-prefix-"
        every { headConfiguration.broadcastChannel } returns "the-broadcast-channel"
        every { headConfiguration.heartbeatChannel } returns "the-heartbeat-channel"
        every { headConfiguration.feedbackChannel } returns "the-feedback-channel"
        every { headConfiguration.heartbeatDuration } returns Duration.ofSeconds(123)
    }

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    internal fun `should consume the handshake requests`() = testCoroutineDispatcher.runTest {
        // given
        val handshakeManager = spyk(
            HandshakeManager(
                environment,
                handshakeHeadChannel,
                idGenerator,
                Optional.of(campaignAutoStarter),
                factoryService,
                headConfiguration,
                this
            ), recordPrivateCalls = true
        )
        val latch = Latch(true)
        val handshakeRequest = relaxedMockk<HandshakeRequest>()
        coEvery { handshakeHeadChannel.onReceiveRequest(any(), any()) } coAnswers {
            launch {
                (secondArg() as suspend (HandshakeRequest) -> Unit).invoke(handshakeRequest)
                latch.release()
            }
        }
        coJustRun { handshakeManager invoke "receivedHandshake" withArguments listOf(refEq(handshakeRequest)) }

        // when
        handshakeManager.init()
        latch.await()

        // then
        coVerify { handshakeManager invoke "receivedHandshake" withArguments listOf(refEq(handshakeRequest)) }
    }

    @Test
    internal fun `should respond to the handshake add a real ID and trigger the campaign auto-starter when present`() =
        testCoroutineDispatcher.runTest {
            // given
            val handshakeManager = HandshakeManager(
                environment,
                handshakeHeadChannel,
                idGenerator,
                Optional.of(campaignAutoStarter),
                factoryService,
                headConfiguration,
                this
            )
            val handshakeRequest = HandshakeRequest(
                nodeId = "_temporary-node-id",
                selectors = mapOf("key1" to "value1", "key2" to "value2"),
                replyTo = "reply-channel",
                scenarios = listOf(
                    relaxedMockk { every { id } returns "scen-1" },
                    relaxedMockk { every { id } returns "scen-2" })
            )
            every { idGenerator.short() } returns "this-id-the-actual-id"
            val latch = Latch(true)
            coEvery { campaignAutoStarter.trigger(any()) } coAnswers { latch.release() }

            // when
            handshakeManager.coInvokeInvisible<Unit>("receivedHandshake", handshakeRequest)
            latch.await()

            // then
            coVerifyOrder {
                factoryService.register(eq("this-id-the-actual-id"), refEq(handshakeRequest))
                handshakeHeadChannel.sendResponse(
                    "reply-channel",
                    HandshakeResponse(
                        handshakeNodeId = "_temporary-node-id",
                        nodeId = "this-id-the-actual-id",
                        unicastDirectivesChannel = "the-unicast-channel-prefix-this-id-the-actual-id",
                        broadcastDirectivesChannel = "the-broadcast-channel",
                        feedbackChannel = "the-feedback-channel",
                        heartbeatChannel = "the-heartbeat-channel",
                        heartbeatPeriod = Duration.ofSeconds(123),
                        unicastContextsChannel = "this-id-the-actual-id",
                        broadcastContextsChannel = "broadcasts-channel"
                    )
                )
                campaignAutoStarter.trigger(eq(listOf("scen-1", "scen-2")))
            }

            confirmVerified(factoryService, handshakeHeadChannel, campaignAutoStarter)
        }

    @Test
    internal fun `should respond to the handshake not not trigger the campaign auto-starter when absent`() =
        testCoroutineDispatcher.runTest {
            testCoroutineDispatcher.runTest {
                // given
                val handshakeManager = HandshakeManager(
                    environment,
                    handshakeHeadChannel,
                    idGenerator,
                    Optional.empty(),
                    factoryService,
                    headConfiguration,
                    this
                )
                val handshakeRequest = HandshakeRequest(
                    nodeId = "a-real-id",
                    selectors = mapOf("key1" to "value1", "key2" to "value2"),
                    replyTo = "reply-channel",
                    scenarios = listOf(
                        relaxedMockk { every { id } returns "scen-1" },
                        relaxedMockk { every { id } returns "scen-2" })
                )

                // when
                handshakeManager.coInvokeInvisible<Unit>("receivedHandshake", handshakeRequest)

                // then
                coVerifyOrder {
                    factoryService.register(eq("a-real-id"), refEq(handshakeRequest))
                    handshakeHeadChannel.sendResponse(
                        "reply-channel",
                        HandshakeResponse(
                            handshakeNodeId = "a-real-id",
                            nodeId = "a-real-id",
                            unicastDirectivesChannel = "the-unicast-channel-prefix-a-real-id",
                            broadcastDirectivesChannel = "the-broadcast-channel",
                            feedbackChannel = "the-feedback-channel",
                            heartbeatChannel = "the-heartbeat-channel",
                            heartbeatPeriod = Duration.ofSeconds(123),
                            unicastContextsChannel = "a-real-id",
                            broadcastContextsChannel = "broadcasts-channel"
                        )
                    )
                }

                confirmVerified(factoryService, handshakeHeadChannel, campaignAutoStarter)
            }
        }
}