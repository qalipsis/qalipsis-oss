package io.qalipsis.core.factory.init

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.core.order.Ordered
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.communication.HandshakeResponseListener
import io.qalipsis.core.factory.configuration.CommunicationChannelConfiguration
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.handshake.RegistrationDirectedAcyclicGraph
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.lifetime.FactoryStartupComponent
import org.slf4j.event.Level
import java.io.File

/**
 * Component in charge of processing the handshake with the head, from the factory perspective.
 *
 * @author Eric Jessé
 */
internal open class InitializationContext(
    private val factoryConfiguration: FactoryConfiguration,
    private val communicationChannelConfiguration: CommunicationChannelConfiguration,
    private val factoryChannel: FactoryChannel
) : FactoryStartupComponent, HandshakeResponseListener {

    override fun getStartupOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    suspend fun startHandshake(scenarios: Collection<Scenario>) {
        val feedbackScenarios = scenarios.map { scenario ->
            val feedbackDags = scenario.dags.map {
                RegistrationDirectedAcyclicGraph(
                    it.id, it.isSingleton, it.isRoot, it.isUnderLoad, it.stepsCount, it.selectors
                )
            }
            RegistrationScenario(
                scenario.id,
                scenario.minionsCount,
                feedbackDags
            )
        }
        // TODO Test
        factoryChannel.subscribeHandshakeResponse(factoryConfiguration.handshake.responseChannel)
        val request = HandshakeRequest(
            factoryConfiguration.nodeId,
            factoryConfiguration.tags,
            factoryConfiguration.handshake.responseChannel,
            feedbackScenarios
        )
        factoryChannel.publishHandshakeRequest(request)
    }

    @LogInput(Level.DEBUG)
    override suspend fun notify(response: HandshakeResponse) {
        if (response.handshakeNodeId == factoryConfiguration.nodeId) {
            log.trace { "Received $response" }
            persistNodeIdIfDifferent(response.nodeId)
            log.trace { "Factory configuration before the update: $factoryConfiguration" }
            factoryConfiguration.nodeId = response.nodeId
            communicationChannelConfiguration.unicastChannel = response.unicastChannel
            log.trace { "Factory configuration is now up-to-date: $factoryConfiguration" }
            factoryChannel.subscribeDirective(response.unicastChannel)
            // Totally stops the handshake response consumption.
            factoryChannel.unsubscribeHandshakeResponse(factoryConfiguration.handshake.responseChannel)
        }
    }

    @KTestable
    protected open fun persistNodeIdIfDifferent(actualNodeId: String) {
        val directory = File(factoryConfiguration.metadataPath)
        val idFile = File(directory, FactoryConfiguration.NODE_ID_FILE_NAME)
        if (!idFile.exists() || actualNodeId != factoryConfiguration.nodeId) {
            try {
                directory.mkdirs()
                idFile.writeText(actualNodeId, Charsets.UTF_8)
            } catch (e: Exception) {
                log.error { e.message }
            }
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()

    }
}