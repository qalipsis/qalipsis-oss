package io.qalipsis.core.head.campaign

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments.AUTOSTART
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.lifetime.HeadStartupComponent
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import org.slf4j.event.Level
import javax.annotation.PreDestroy

/**
 * Component to automatically starts the execution of a campaign with all the scenarios as soon as
 * a [FactoryRegistrationRequest] is started.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(Requires(env = [AUTOSTART]), Requires(env = [STANDALONE]))
internal class CampaignAutoStarter(
    private val factoryService: FactoryService,
    private val campaignManager: CampaignManager,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
    private val autostartCampaignConfiguration: AutostartCampaignConfiguration
) : ProcessBlocker, HeadStartupComponent, DirectiveProcessor<CompleteCampaignDirective> {

    @KTestable
    private val campaignLatch = Latch(true)

    private var feedbackConsumptionJob: Job? = null

    override fun getStartupOrder() = Int.MIN_VALUE + 1

    private var error: String? = null

    @LogInput(level = Level.DEBUG)
    suspend fun trigger(scenarios: List<ScenarioId>) {
        if (scenarios.isNotEmpty()) {
            log.info { "Triggering the campaign ${autostartCampaignConfiguration.id} for the scenario(s) ${scenarios.joinToString()}" }
            campaignLatch.lock()
            val scenariosConfigs = factoryService.getActiveScenarios(scenarios).associate { scenario ->
                scenario.id to ScenarioConfiguration(calculateMinionsCount(scenario))
            }
            val campaign = CampaignConfiguration(
                id = autostartCampaignConfiguration.id,
                speedFactor = autostartCampaignConfiguration.speedFactor,
                startOffsetMs = autostartCampaignConfiguration.startOffsetMs,
                broadcastChannel = "",
                scenarios = scenariosConfigs,
                factories = mutableMapOf()
            )
            campaignManager.start(campaign)
        } else {
            log.error { "No executable scenario was found" }
            error = "No executable scenario was found"
            campaignReportStateKeeper.abort(autostartCampaignConfiguration.id)
            campaignLatch.release()
        }
    }

    @KTestable
    private fun calculateMinionsCount(scenario: ScenarioSummary) =
        if (autostartCampaignConfiguration.minionsCountPerScenario > 0) autostartCampaignConfiguration.minionsCountPerScenario else (scenario.minionsCount * autostartCampaignConfiguration.minionsFactor).toInt()

    override fun accept(directive: Directive): Boolean {
        return directive is CompleteCampaignDirective
    }

    @LogInput
    override suspend fun process(directive: CompleteCampaignDirective) {
        // Ensure that the latch was incremented before it is decremented, in case the scenario is too fast.
        if (directive.isSuccessful) {
            log.info { "The campaign ${directive.campaignId} was completed successfully: ${directive.message ?: "<no detail>"}" }
        } else {
            log.error { "The campaign ${directive.campaignId} failed: ${directive.message ?: "<no detail>"}" }
            error = directive.message
        }
        campaignLatch.release()
    }

    override suspend fun join() {
        campaignLatch.await()
        error?.let { throw RuntimeException(it) }
    }

    @PreDestroy
    override fun cancel() {
        runCatching {
            feedbackConsumptionJob?.cancel()
        }
        campaignLatch.cancel()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
