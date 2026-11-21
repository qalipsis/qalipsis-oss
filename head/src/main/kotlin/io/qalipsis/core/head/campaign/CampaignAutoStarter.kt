/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.head.campaign

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessageSeverity
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
import io.qalipsis.core.lifetime.ProcessExitCodeSupplier
import jakarta.annotation.PreDestroy
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.event.Level
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean

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
class CampaignAutoStarter(
    private val factoryService: FactoryService,
    private val campaignExecutor: Provider<CampaignExecutor>,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
    private val autostartCampaignConfiguration: AutostartCampaignConfiguration,
    private val headChannel: HeadChannel
) : ProcessBlocker, ProcessExitCodeSupplier, HeadStartupComponent, HeartbeatListener, HandshakeRequestListener,
    CampaignReportPublisher {

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

    private var exitCode: Int? = null

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
                        startCampaign()
                    } else {
                        exitCode = EXIT_CODE_NO_SCENARIO
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

    /**
     * Starts the campaign with all the registered scenarios. When the campaign cannot be started (e.g. when it does
     * not pass the validation), the error is reported and the process blocker is released, so that the process
     * exits with [EXIT_CODE_START_FAILURE] instead of waiting forever for a campaign that will never run.
     */
    private suspend fun startCampaign() {
        try {
            val scenariosConfigs =
                factoryService.getActiveScenarios(
                    Defaults.TENANT,
                    registeredScenarios
                ).associate { scenario ->
                    scenario.name to ScenarioRequest(calculateMinionsCount(scenario))
                }
            campaign = campaignExecutor.get().start(
                tenant = Defaults.TENANT,
                configurer = Defaults.USERNAME,
                configuration = CampaignConfiguration(
                    name = autostartCampaignConfiguration.name,
                    speedFactor = autostartCampaignConfiguration.speedFactor,
                    startOffsetMs = autostartCampaignConfiguration.startOffset.toMillis(),
                    scenarios = scenariosConfigs
                )
            )
            autostartCampaignConfiguration.generatedKey = campaign.key
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            exitCode = EXIT_CODE_START_FAILURE
            error = e.message ?: e.javaClass.simpleName
            log.error { "The campaign ${autostartCampaignConfiguration.name} could not be started: $error" }
            campaignLatch.release()
        }
    }

    @KTestable
    private fun calculateMinionsCount(scenario: ScenarioSummary) =
        if (autostartCampaignConfiguration.minionsCountPerScenario > 0) autostartCampaignConfiguration.minionsCountPerScenario else (scenario.minionsCount * autostartCampaignConfiguration.minionsFactor).toInt()

    override suspend fun publish(
        tenant: String,
        campaignKey: CampaignKey,
        report: CampaignReport
    ) {
        if (report.status == ExecutionStatus.SUCCESSFUL) {
            log.info { "The campaign $campaignKey was successfully completed" }
        } else {
            exitCode = EXIT_CODE_CAMPAIGN_FAILURE
            val errors = report.scenariosReports
                .filter { it.status != ExecutionStatus.SUCCESSFUL }
                .flatMap { it.messages }
                .filter { it.severity != ReportMessageSeverity.INFO }
                .map { "Step ${it.stepName}: ${it.message}" }
            error = errors.joinToString()

            log.error {
                "The campaign $campaignKey completed with errors: ${
                    errors.joinToString(
                        "\n\t",
                        "\n\t",
                        "\n"
                    )
                }"
            }
        }
    }

    @LogInput
    suspend fun completeCampaign(directive: CompleteCampaignDirective) {
        if (!directive.isSuccessful) {
            exitCode = EXIT_CODE_CAMPAIGN_FAILURE
        }
        campaign.factories.forEach { (_, factory) ->
            headChannel.publishDirective(FactoryShutdownDirective(factory.unicastChannel))
        }
        runningCampaign.set(false)
        campaignLatch.release()
    }

    override suspend fun await(): Optional<Int> {
        campaignLatch.await()
        return Optional.ofNullable(exitCode)
    }

    override suspend fun join() {
        campaignLatch.await()
    }

    override fun getOrder(): Int {
        return Int.MAX_VALUE
    }

    @PreDestroy
    override fun cancel() {
        campaignLatch.cancel()
    }

    companion object {

        /**
         * Exit code when no executable scenario was found.
         */
        const val EXIT_CODE_NO_SCENARIO = 201

        /**
         * Exit code when the campaign was executed but completed with failures.
         */
        const val EXIT_CODE_CAMPAIGN_FAILURE = 202

        /**
         * Exit code when the campaign could not be started, e.g. when its configuration is invalid.
         */
        const val EXIT_CODE_START_FAILURE = 203

        @JvmStatic
        private val log = logger()
    }
}
