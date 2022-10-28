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

package io.qalipsis.core.head.campaign

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.FactoryShutdownDirective
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.communication.HeartbeatListener
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.lifetime.HeadStartupComponent
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.event.Level
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

/**
 * Component to automatically starts the execution of a campaign with all the scenarios as soon as
 * enough factories are registered.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.AUTOSTART])
)
internal class CampaignAutoStarter(
    private val factoryService: FactoryService,
    private val campaignManager: Provider<CampaignManager>,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
    private val autostartCampaignConfiguration: AutostartCampaignConfiguration,
    private val headChannel: HeadChannel
) : ProcessBlocker, HeadStartupComponent, HeartbeatListener, HandshakeRequestListener {

    @KTestable
    private val campaignLatch = Latch(true, "campaign-auto-starter")

    /**
     * Error of the autostart or campaign execution.
     */
    private var error: String? = null

    /**
     * Set of all the healthy factories.
     */
    private val healthyFactories = concurrentSet<NodeId>()

    /**
     * Set of all the declared scenarios.
     */
    private val registeredScenarios = concurrentSet<ScenarioName>()

    /**
     * Ensures that the campaign cannot be started before the required [HandshakeRequest] were all received.
     */
    private val registrationCount = SuspendedCountLatch(autostartCampaignConfiguration.requiredFactories.toLong(), true)

    private val notificationMutex = Mutex(false)

    private var runningCampaign = AtomicBoolean(false)

    @KTestable
    private lateinit var campaign: RunningCampaign

    override fun getStartupOrder() = Int.MIN_VALUE + 1

    @LogInput(Level.DEBUG)
    override suspend fun notify(handshakeRequest: HandshakeRequest) {
        registeredScenarios += handshakeRequest.scenarios.map { it.name }
        registrationCount.decrement()
    }

    @LogInput(Level.DEBUG)
    override suspend fun notify(heartbeat: Heartbeat) {
        registrationCount.await()
        notificationMutex.withLock {
            log.debug { "Heartbeat: ${heartbeat.nodeId} to ${heartbeat.state}" }
            if (heartbeat.state == Heartbeat.State.IDLE && healthyFactories.add(heartbeat.nodeId)) {
                log.debug { "Healthy factories: $healthyFactories" }
                if (healthyFactories.size >= autostartCampaignConfiguration.requiredFactories
                    && runningCampaign.compareAndSet(false, true)
                ) {
                    log.debug { "Registered scenarios: $registeredScenarios" }
                    if (registeredScenarios.isNotEmpty()) {
                        val delay = autostartCampaignConfiguration.triggerOffset.toMillis()
                        log.info { "Starting the campaign ${autostartCampaignConfiguration.name} for the scenario(s) ${registeredScenarios.joinToString()} in $delay ms" }
                        delay(delay)
                        campaignLatch.lock()
                        val scenariosConfigs =
                            factoryService.getActiveScenarios(
                                Defaults.TENANT,
                                registeredScenarios
                            ).associate { scenario ->
                                scenario.name to ScenarioRequest(calculateMinionsCount(scenario))
                            }
                        campaign = campaignManager.get().start(
                            tenant = Defaults.TENANT,
                            configurer = Defaults.USER,
                            configuration = CampaignConfiguration(
                                name = autostartCampaignConfiguration.name,
                                speedFactor = autostartCampaignConfiguration.speedFactor,
                                startOffsetMs = autostartCampaignConfiguration.startOffset.toMillis(),
                                scenarios = scenariosConfigs
                            )
                        )
                        autostartCampaignConfiguration.generatedKey = campaign.key
                    } else {
                        log.error { "No executable scenario was found" }
                        error = "No executable scenario was found"
                        // Call the abort to generate a failure.
                        campaignReportStateKeeper.abort(autostartCampaignConfiguration.name)
                        campaignLatch.release()
                    }
                }
            }
        }
    }

    @KTestable
    private fun calculateMinionsCount(scenario: ScenarioSummary) =
        if (autostartCampaignConfiguration.minionsCountPerScenario > 0) autostartCampaignConfiguration.minionsCountPerScenario else (scenario.minionsCount * autostartCampaignConfiguration.minionsFactor).toInt()

    @LogInput
    suspend fun completeCampaign(directive: CompleteCampaignDirective) {
        if (directive.isSuccessful) {
            log.info { "The campaign ${directive.campaignKey} was completed successfully: ${directive.message ?: "<no detail>"}" }
        } else {
            log.error { "The campaign ${directive.campaignKey} failed: ${directive.message ?: "<no detail>"}" }
            error = directive.message?.takeIf { it.isNotBlank() }
        }
        campaign.factories.forEach { (_, factory) ->
            headChannel.publishDirective(FactoryShutdownDirective(factory.unicastChannel))
        }
        runningCampaign.set(false)
        campaignLatch.release()
    }

    override suspend fun join() {
        campaignLatch.await()
        error?.let {
            log.error { "An error occurred while executing the campaign: $error" }
            throw RuntimeException(it)
        }
    }

    @PreDestroy
    override fun cancel() {
        campaignLatch.cancel()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
