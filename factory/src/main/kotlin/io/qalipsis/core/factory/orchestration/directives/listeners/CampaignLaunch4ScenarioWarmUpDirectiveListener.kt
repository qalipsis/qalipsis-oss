package io.qalipsis.core.factory.orchestration.directives.listeners

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Service responsible for starting the components and singleton minions of
 * a scenario in the context of a new test campaign.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch4ScenarioWarmUpDirectiveListener(
    private val localAssignmentStore: LocalAssignmentStore,
    private val factoryCampaignManager: FactoryCampaignManager,
    private val factoryChannel: FactoryChannel
) : DirectiveListener<ScenarioWarmUpDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is ScenarioWarmUpDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignKey, directive.scenarioName)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: ScenarioWarmUpDirective) {
        if (localAssignmentStore.hasMinionsAssigned(directive.scenarioName)) {
            val feedback = ScenarioWarmUpFeedback(
                campaignKey = directive.campaignKey,
                scenarioName = directive.scenarioName,
                status = FeedbackStatus.IN_PROGRESS
            )
            factoryChannel.publishFeedback(feedback)
            try {
                factoryCampaignManager.warmUpCampaignScenario(directive.campaignKey, directive.scenarioName)
                factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
            } catch (e: Exception) {
                log.error(e) { e.message }
                factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.FAILED, error = e.message))
            }
        } else {
            val feedback = ScenarioWarmUpFeedback(
                campaignKey = directive.campaignKey,
                scenarioName = directive.scenarioName,
                status = FeedbackStatus.IGNORED
            )
            factoryChannel.publishFeedback(feedback)
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
