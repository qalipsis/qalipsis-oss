package io.qalipsis.core.factory.init

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import com.google.common.io.Files
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.justRun
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.directives.DirectiveConsumer
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.init.catadioptre.persistNodeIdIfDifferent
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.handshake.HandshakeFactoryChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.heartbeat.HeartbeatEmitter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.time.Duration

@WithMockk
internal class InitializationContextTest {

    @RegisterExtension
    private val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var handshakeFactoryChannel: HandshakeFactoryChannel

    @RelaxedMockK
    private lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    private lateinit var heartbeatEmitter: HeartbeatEmitter

    @RelaxedMockK
    private lateinit var directiveConsumer: DirectiveConsumer

    @Test
    @Timeout(3)
    internal fun `should publish handshake request for all scenarios`() = testDispatcherProvider.runTest {
        // given
        val scenario1: Scenario = relaxedMockk {
            every { id } returns "scenario-1"
            every { minionsCount } returns 2
            every { dags } returns mutableListOf(
                relaxedMockk {
                    every { id } returns "dag-1"
                    every { isSingleton } returns false
                    every { isRoot } returns true
                    every { stepsCount } returns 12
                    every { selectors } returns mutableMapOf("key1" to "value1")
                },
                relaxedMockk {
                    every { id } returns "dag-2"
                    every { isSingleton } returns true
                    every { isRoot } returns false
                    every { stepsCount } returns 4
                    every { selectors } returns mutableMapOf("key2" to "value2")
                }
            )
        }
        val scenario2: Scenario = relaxedMockk {
            every { id } returns "scenario-2"
            every { minionsCount } returns 1
            every { dags } returns mutableListOf(
                relaxedMockk {
                    every { id } returns "dag-3"
                    every { isSingleton } returns false
                    every { isRoot } returns true
                    every { isUnderLoad } returns true
                    every { stepsCount } returns 42
                    every { selectors } returns mutableMapOf("key3" to "value3", "key4" to "value4")
                }
            )
        }
        val request = slot<HandshakeRequest>()
        coJustRun { handshakeFactoryChannel.send(capture(request)) }
        val factoryConfiguration = FactoryConfiguration().apply {
            nodeId = "the-node-id"
            selectors = mapOf("key1" to "value1", "key2" to "value2")
            handshakeResponseChannel = "the-handshake-response-channel"
        }
        val initializationContext = InitializationContext(
            factoryConfiguration,
            feedbackFactoryChannel,
            directiveConsumer,
            handshakeFactoryChannel,
            heartbeatEmitter,
            testDispatcherProvider.default(),
            this
        )

        // when
        initializationContext.startHandshake(listOf(scenario1, scenario2))

        // then
        coVerifyOnce {
            handshakeFactoryChannel.send(any())
        }
        assertThat(request.captured).all {
            prop(HandshakeRequest::nodeId).isEqualTo("the-node-id")
            prop(HandshakeRequest::selectors).isEqualTo(mapOf("key1" to "value1", "key2" to "value2"))
            prop(HandshakeRequest::replyTo).isEqualTo("the-handshake-response-channel")
            prop(HandshakeRequest::scenarios).all {
                hasSize(2)
                index(0).all {
                    prop(RegistrationScenario::id).isEqualTo("scenario-1")
                    prop(RegistrationScenario::minionsCount).isEqualTo(2)
                    prop(RegistrationScenario::directedAcyclicGraphs).all {
                        hasSize(2)
                        index(0).all {
                            prop(DirectedAcyclicGraphSummary::id).isEqualTo("dag-1")
                            prop(DirectedAcyclicGraphSummary::isSingleton).isFalse()
                            prop(DirectedAcyclicGraphSummary::isRoot).isTrue()
                            prop(DirectedAcyclicGraphSummary::isUnderLoad).isFalse()
                            prop(DirectedAcyclicGraphSummary::numberOfSteps).isEqualTo(12)
                            prop(DirectedAcyclicGraphSummary::selectors).all {
                                hasSize(1)
                                key("key1").isEqualTo("value1")
                            }
                        }
                        index(1).all {
                            prop(DirectedAcyclicGraphSummary::id).isEqualTo("dag-2")
                            prop(DirectedAcyclicGraphSummary::isSingleton).isTrue()
                            prop(DirectedAcyclicGraphSummary::isRoot).isFalse()
                            prop(DirectedAcyclicGraphSummary::isUnderLoad).isFalse()
                            prop(DirectedAcyclicGraphSummary::numberOfSteps).isEqualTo(4)
                            prop(DirectedAcyclicGraphSummary::selectors).all {
                                hasSize(1)
                                key("key2").isEqualTo("value2")
                            }
                        }
                    }
                }

                index(1).all {
                    prop(RegistrationScenario::id).isEqualTo("scenario-2")
                    prop(RegistrationScenario::minionsCount).isEqualTo(1)
                    prop(RegistrationScenario::directedAcyclicGraphs).all {
                        hasSize(1)
                        index(0).all {
                            prop(DirectedAcyclicGraphSummary::id).isEqualTo("dag-3")
                            prop(DirectedAcyclicGraphSummary::isSingleton).isFalse()
                            prop(DirectedAcyclicGraphSummary::isRoot).isTrue()
                            prop(DirectedAcyclicGraphSummary::isUnderLoad).isTrue()
                            prop(DirectedAcyclicGraphSummary::numberOfSteps).isEqualTo(42)
                            prop(DirectedAcyclicGraphSummary::selectors).all {
                                hasSize(2)
                                key("key3").isEqualTo("value3")
                                key("key4").isEqualTo("value4")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @Timeout(3)
    internal fun `should consume and process the handshake response`() = testDispatcherProvider.run {
        // given
        val handshakeResponse = relaxedMockk<HandshakeResponse>()
        val factoryConfiguration = FactoryConfiguration().apply {
            nodeId = "the-node-id"
            metadataPath = Files.createTempDir().path
        }
        val initializationContext = spyk(
            InitializationContext(
                factoryConfiguration,
                feedbackFactoryChannel,
                directiveConsumer,
                handshakeFactoryChannel,
                heartbeatEmitter,
                testDispatcherProvider.default(),
                this
            ), recordPrivateCalls = true
        )
        val latch = Latch(true)
        val consumerJob = relaxedMockk<Job>()
        coEvery { initializationContext["configureFactoryAfterHandshake"](any<HandshakeResponse>()) } coAnswers { latch.release() }
        coEvery { handshakeFactoryChannel.onReceiveResponse(any(), any()) } coAnswers {
            secondArg<suspend (HandshakeResponse) -> Unit>().invoke(handshakeResponse)
            consumerJob
        }

        // when
        initializationContext.init()
        latch.await()

        // then
        coVerifyOnce {
            initializationContext["configureFactoryAfterHandshake"](refEq(handshakeResponse))
        }

        initializationContext.close()
    }

    @Test
    internal fun `should update the node ID and start the consumers`() = testDispatcherProvider.run {
        // given
        val factoryConfiguration = FactoryConfiguration().apply {
            nodeId = "the-node-id"
            metadataPath = Files.createTempDir().path
        }
        val handshakeResponse = HandshakeResponse(
            handshakeNodeId = "the-node-id",
            nodeId = "the-actual-node-id",
            unicastDirectivesChannel = "the-unicast-directive",
            broadcastDirectivesChannel = "the-broadcast-directive",
            feedbackChannel = "the-feedback-directive",
            heartbeatChannel = "the-heartbeat-directive",
            heartbeatPeriod = Duration.ofMinutes(1),
            unicastContextsChannel = "the-unicast-context",
            broadcastContextsChannel = "the-broadcast-context"
        )
        val initializationContext = spyk(
            InitializationContext(
                factoryConfiguration,
                feedbackFactoryChannel,
                directiveConsumer,
                handshakeFactoryChannel,
                heartbeatEmitter,
                testDispatcherProvider.default(),
                this
            ), recordPrivateCalls = true
        )
        justRun { initializationContext["persistNodeIdIfDifferent"]("the-actual-node-id") }

        // when
        initializationContext.coInvokeInvisible<Unit>("configureFactoryAfterHandshake", handshakeResponse)

        // then
        assertThat(factoryConfiguration.nodeId).isEqualTo("the-actual-node-id")
        coVerifyOnce {
            initializationContext["persistNodeIdIfDifferent"]("the-actual-node-id")
            heartbeatEmitter.start("the-actual-node-id", "the-heartbeat-directive", Duration.ofMinutes(1))
            feedbackFactoryChannel.start("the-feedback-directive")
            directiveConsumer.start("the-unicast-directive", "the-broadcast-directive")
            handshakeFactoryChannel.close()
        }
    }

    @Test
    internal fun `should not persist the ID in the file if it is the same`() = testDispatcherProvider.run {
        // given
        val directory = Files.createTempDir()
        val factoryConfiguration = FactoryConfiguration().apply {
            nodeId = "the-node-id"
            metadataPath = directory.path
        }
        val initializationContext = spyk(
            InitializationContext(
                factoryConfiguration,
                feedbackFactoryChannel,
                directiveConsumer,
                handshakeFactoryChannel,
                heartbeatEmitter,
                testDispatcherProvider.default(),
                relaxedMockk()
            ), recordPrivateCalls = true
        )
        val idFileContent = """# This is the ID
the-node-id""".trimIndent()
        val idFile = File(directory, FactoryConfiguration.NODE_ID_FILE_NAME)
        idFile.writer(Charsets.UTF_8).use { it.write(idFileContent) }

        // when
        initializationContext.persistNodeIdIfDifferent("the-node-id")

        // then
        val contentAfter = idFile.readText(Charsets.UTF_8).trim()
        assertThat(contentAfter).isEqualTo(idFileContent)
    }

    @Test
    internal fun `should persist the ID in the file if different`() = testDispatcherProvider.runTest {
        // given
        val directory = Files.createTempDir()
        val factoryConfiguration = FactoryConfiguration().apply {
            nodeId = "the-persisted-id"
            metadataPath = directory.path
        }
        val initializationContext = InitializationContext(
            factoryConfiguration,
            feedbackFactoryChannel,
            directiveConsumer,
            handshakeFactoryChannel,
            heartbeatEmitter,
            testDispatcherProvider.default(),
            relaxedMockk()
        )
        val idFileContent = """
# This is the ID
the-persisted-id
        """.trimIndent()
        val idFile = File(directory, FactoryConfiguration.NODE_ID_FILE_NAME)
        idFile.writer(Charsets.UTF_8).use { it.write(idFileContent) }

        // when
        initializationContext.persistNodeIdIfDifferent("the-actual-id")

        // then
        val contentAfter = idFile.readText(Charsets.UTF_8).trim()
        assertThat(contentAfter).isEqualTo("the-actual-id")
    }

    @Test
    internal fun `should persist the ID in the file if it does not exist`() = testDispatcherProvider.run {
        // given
        val directory = File(Files.createTempDir(), "metadata")
        val factoryConfiguration = FactoryConfiguration().apply {
            nodeId = "the-node-id"
            metadataPath = directory.path
        }
        val initializationContext = InitializationContext(
            factoryConfiguration,
            feedbackFactoryChannel,
            directiveConsumer,
            handshakeFactoryChannel,
            heartbeatEmitter,
            testDispatcherProvider.default(),
            relaxedMockk()
        )

        // when
        initializationContext.persistNodeIdIfDifferent("the-actual-id")

        // then
        val idFile = File(directory, FactoryConfiguration.NODE_ID_FILE_NAME)
        val contentAfter = idFile.readText(Charsets.UTF_8).trim()
        assertThat(contentAfter).isEqualTo("the-actual-id")
    }

}