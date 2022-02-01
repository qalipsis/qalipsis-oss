package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
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
internal class CampaignLaunch4ScenarioWarmUpDirectiveProcessor(
    private val localAssignmentStore: LocalAssignmentStore,
    private val factoryCampaignManager: FactoryCampaignManager,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<ScenarioWarmUpDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is ScenarioWarmUpDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId, directive.scenarioId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: ScenarioWarmUpDirective) {
        if (localAssignmentStore.hasMinionsAssigned(directive.scenarioId)) {
            val feedback = ScenarioWarmUpFeedback(
                key = idGenerator.short(),
                campaignId = directive.campaignId,
                scenarioId = directive.scenarioId,
                nodeId = factoryCampaignManager.feedbackNodeId,
                status = FeedbackStatus.IN_PROGRESS
            )
            feedbackFactoryChannel.publish(feedback)
            try {
                factoryCampaignManager.warmUpCampaignScenario(directive.campaignId, directive.scenarioId)
                feedbackFactoryChannel.publish(
                    feedback.copy(
                        key = idGenerator.short(),
                        status = FeedbackStatus.COMPLETED
                    )
                )
            } catch (e: Exception) {
                log.error(e) { e.message }
                feedbackFactoryChannel.publish(
                    feedback.copy(
                        key = idGenerator.short(),
                        status = FeedbackStatus.FAILED,
                        error = e.message
                    )
                )
            }
        } else {
            val feedback = ScenarioWarmUpFeedback(
                key = idGenerator.short(),
                campaignId = directive.campaignId,
                scenarioId = directive.scenarioId,
                nodeId = factoryCampaignManager.feedbackNodeId,
                status = FeedbackStatus.IGNORED
            )
            feedbackFactoryChannel.publish(feedback)
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
