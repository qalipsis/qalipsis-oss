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
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.justRun
import io.mockk.slot
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.CommunicationChannelConfiguration
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.init.catadioptre.persistNodeIdIfDifferent
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.time.Duration

@WithMockk
internal class InitializationContextTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    @RelaxedMockK
    private lateinit var communicationChannelConfiguration: CommunicationChannelConfiguration

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @InjectMockKs
    @SpyK(recordPrivateCalls = true)
    private lateinit var initializationContext: InitializationContext

    @Test
    @Timeout(3)
    internal fun `should publish handshake request for all scenarios`() = testDispatcherProvider.runTest {
        // given
        every { factoryConfiguration.nodeId } returns "the-node-id"
        every { factoryConfiguration.tags } returns mapOf("key1" to "value1", "key2" to "value2")
        every { factoryConfiguration.handshake.responseChannel } returns "the-handshake-response-channel"
        val scenario1: Scenario = relaxedMockk {
            every { name } returns "scenario-1"
            every { minionsCount } returns 2
            every { dags } returns mutableListOf(
                relaxedMockk {
                    every { name } returns "dag-1"
                    every { isSingleton } returns false
                    every { isRoot } returns true
                    every { stepsCount } returns 12
                    every { selectors } returns mutableMapOf("key1" to "value1")
                },
                relaxedMockk {
                    every { name } returns "dag-2"
                    every { isSingleton } returns true
                    every { isRoot } returns false
                    every { stepsCount } returns 4
                    every { selectors } returns mutableMapOf("key2" to "value2")
                }
            )
        }
        val scenario2: Scenario = relaxedMockk {
            every { name } returns "scenario-2"
            every { minionsCount } returns 1
            every { dags } returns mutableListOf(
                relaxedMockk {
                    every { name } returns "dag-3"
                    every { isSingleton } returns false
                    every { isRoot } returns true
                    every { isUnderLoad } returns true
                    every { stepsCount } returns 42
                    every { selectors } returns mutableMapOf("key3" to "value3", "key4" to "value4")
                }
            )
        }
        val request = slot<HandshakeRequest>()
        coJustRun { factoryChannel.publishHandshakeRequest(capture(request)) }

        // when
        initializationContext.startHandshake(listOf(scenario1, scenario2))

        // then
        coVerifyOnce {
            factoryChannel.publishHandshakeRequest(any())
        }
        assertThat(request.captured).all {
            prop(HandshakeRequest::nodeId).isEqualTo("the-node-id")
            prop(HandshakeRequest::tags).isEqualTo(mapOf("key1" to "value1", "key2" to "value2"))
            prop(HandshakeRequest::replyTo).isEqualTo("the-handshake-response-channel")
            prop(HandshakeRequest::scenarios).all {
                hasSize(2)
                index(0).all {
                    prop(RegistrationScenario::name).isEqualTo("scenario-1")
                    prop(RegistrationScenario::minionsCount).isEqualTo(2)
                    prop(RegistrationScenario::directedAcyclicGraphs).all {
                        hasSize(2)
                        index(0).all {
                            prop(DirectedAcyclicGraphSummary::name).isEqualTo("dag-1")
                            prop(DirectedAcyclicGraphSummary::isSingleton).isFalse()
                            prop(DirectedAcyclicGraphSummary::isRoot).isTrue()
                            prop(DirectedAcyclicGraphSummary::isUnderLoad).isFalse()
                            prop(DirectedAcyclicGraphSummary::numberOfSteps).isEqualTo(12)
                            prop(DirectedAcyclicGraphSummary::tags).all {
                                hasSize(1)
                                key("key1").isEqualTo("value1")
                            }
                        }
                        index(1).all {
                            prop(DirectedAcyclicGraphSummary::name).isEqualTo("dag-2")
                            prop(DirectedAcyclicGraphSummary::isSingleton).isTrue()
                            prop(DirectedAcyclicGraphSummary::isRoot).isFalse()
                            prop(DirectedAcyclicGraphSummary::isUnderLoad).isFalse()
                            prop(DirectedAcyclicGraphSummary::numberOfSteps).isEqualTo(4)
                            prop(DirectedAcyclicGraphSummary::tags).all {
                                hasSize(1)
                                key("key2").isEqualTo("value2")
                            }
                        }
                    }
                }

                index(1).all {
                    prop(RegistrationScenario::name).isEqualTo("scenario-2")
                    prop(RegistrationScenario::minionsCount).isEqualTo(1)
                    prop(RegistrationScenario::directedAcyclicGraphs).all {
                        hasSize(1)
                        index(0).all {
                            prop(DirectedAcyclicGraphSummary::name).isEqualTo("dag-3")
                            prop(DirectedAcyclicGraphSummary::isSingleton).isFalse()
                            prop(DirectedAcyclicGraphSummary::isRoot).isTrue()
                            prop(DirectedAcyclicGraphSummary::isUnderLoad).isTrue()
                            prop(DirectedAcyclicGraphSummary::numberOfSteps).isEqualTo(42)
                            prop(DirectedAcyclicGraphSummary::tags).all {
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
    internal fun `should update the node ID`() = testDispatcherProvider.run {
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
        }
    }

    @Test
    internal fun `should not persist the ID in the file if it is the same`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.nodeId } returns "the-node-id"
        val directory = Files.createTempDir()
        every { factoryConfiguration.metadataPath } returns directory.path

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
        every { factoryConfiguration.nodeId } returns "the-persisted-id"
        val directory = Files.createTempDir()
        every { factoryConfiguration.metadataPath } returns directory.path
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
        every { factoryConfiguration.nodeId } returns "the-node-id"
        val directory = File(Files.createTempDir(), "metadata")
        every { factoryConfiguration.metadataPath } returns directory.path

        // when
        initializationContext.persistNodeIdIfDifferent("the-actual-id")

        // then
        val idFile = File(directory, FactoryConfiguration.NODE_ID_FILE_NAME)
        val contentAfter = idFile.readText(Charsets.UTF_8).trim()
        assertThat(contentAfter).isEqualTo("the-actual-id")
    }

}