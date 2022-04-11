package io.qalipsis.core.head.campaign

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.FactoryShutdownDirective
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.communication.HeartbeatListener
import io.qalipsis.core.head.factory.FactoryService
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

    private var runningCampaign = false

    @KTestable
    private lateinit var campaign: CampaignConfiguration

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
            if (heartbeat.state == Heartbeat.State.HEALTHY) {
                healthyFactories += heartbeat.nodeId
                if (healthyFactories.size >= autostartCampaignConfiguration.requiredFactories && !runningCampaign) {
                    if (registeredScenarios.isNotEmpty()) {
                        delay(autostartCampaignConfiguration.triggerOffset.toMillis())
                        log.info { "Starting the campaign ${autostartCampaignConfiguration.name} for the scenario(s) ${registeredScenarios.joinToString()}" }
                        campaignLatch.lock()
                        val scenariosConfigs =
                            factoryService.getActiveScenarios(
                                autostartCampaignConfiguration.tenant,
                                registeredScenarios
                            ).associate { scenario ->
                                scenario.name to ScenarioConfiguration(calculateMinionsCount(scenario))
                            }
                        campaign = CampaignConfiguration(
                            name = autostartCampaignConfiguration.name,
                            speedFactor = autostartCampaignConfiguration.speedFactor,
                            startOffsetMs = autostartCampaignConfiguration.startOffset.toMillis(),
                            scenarios = scenariosConfigs
                        )
                        campaignManager.get().start(campaign)
                        runningCampaign = true
                    } else {
                        log.error { "No executable scenario was found" }
                        error = "No executable scenario was found"
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
            log.info { "The campaign ${directive.campaignName} was completed successfully: ${directive.message ?: "<no detail>"}" }
        } else {
            log.error { "The campaign ${directive.campaignName} failed: ${directive.message ?: "<no detail>"}" }
            error = directive.message
        }
        campaign.factories.forEach { (_, factory) ->
            headChannel.publishDirective(FactoryShutdownDirective(factory.unicastChannel))
        }
        campaignLatch.release()
    }

    override suspend fun join() {
        campaignLatch.await()
        error?.let { throw RuntimeException(it) }
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
