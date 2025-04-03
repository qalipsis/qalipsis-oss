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
        val scenariosToRegister = scenarios.map { scenario ->
            val scenariosDags = scenario.dags.map {
                RegistrationDirectedAcyclicGraph(
                    name = it.name,
                    isSingleton = it.isSingleton,
                    isRoot = it.isRoot,
                    isUnderLoad = it.isUnderLoad,
                    numberOfSteps = it.stepsCount,
                    tags = it.tags
                )
            }
            RegistrationScenario(
                name = scenario.name,
                description = scenario.description,
                version = scenario.version,
                builtAt = scenario.builtAt,
                minionsCount = scenario.minionsCount,
                directedAcyclicGraphs = scenariosDags
            )
        }
        factoryChannel.subscribeHandshakeResponse(factoryConfiguration.handshake.responseChannel)
        val request = HandshakeRequest(
            factoryConfiguration.nodeId,
            factoryConfiguration.tags,
            factoryConfiguration.handshake.responseChannel,
            scenariosToRegister,
            factoryConfiguration.tenant,
            factoryConfiguration.zone
        )
        factoryChannel.publishHandshakeRequest(request)
    }

    @LogInput(Level.DEBUG)
    override suspend fun notify(response: HandshakeResponse) {
        // Totally stops the handshake response consumption.
        factoryChannel.unsubscribeHandshakeResponse(factoryConfiguration.handshake.responseChannel)
        log.trace { "Received $response" }
        persistNodeIdIfDifferent(response.nodeId)
        log.trace { "Factory configuration before the update: $factoryConfiguration" }
        factoryConfiguration.nodeId = response.nodeId
        communicationChannelConfiguration.unicastChannel = response.unicastChannel
        log.trace { "Factory configuration is now up-to-date: $factoryConfiguration" }
        factoryChannel.subscribeDirective(response.unicastChannel)
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