package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import jakarta.inject.Singleton

/**
 * The [CampaignLaunch1FactoryAssignmentDirectiveProcessor] is responsible for saving the assignment of the DAGS
 * into the current factory for the starting scenario.
 *
 * @author Eric Jessé
 */
@Singleton
internal class CampaignLaunch1FactoryAssignmentDirectiveProcessor(
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryCampaignManager: FactoryCampaignManager,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<FactoryAssignmentDirective> {

    override fun accept(directive: Directive): Boolean {
        return directive is FactoryAssignmentDirective
    }

    override suspend fun process(directive: FactoryAssignmentDirective) {
        val feedback = FactoryAssignmentFeedback(
            key = idGenerator.short(),
            campaignId = directive.campaignId,
            nodeId = factoryCampaignManager.feedbackNodeId,
            status = FeedbackStatus.IN_PROGRESS
        )
        feedbackFactoryChannel.publish(feedback)
        try {
            factoryCampaignManager.initCampaign(directive.campaignId, directive.assignedDagsByScenario.keys)
            minionAssignmentKeeper.assignFactoryDags(directive.campaignId, directive.assignedDagsByScenario)
            feedbackFactoryChannel.publish(feedback.copy(key = idGenerator.short(), status = FeedbackStatus.COMPLETED))
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
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}