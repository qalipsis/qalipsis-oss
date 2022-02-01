package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.head.campaign.CampaignConfiguration

internal open class FactoryAssignmentState(
    protected val campaign: CampaignConfiguration
) :
    AbstractCampaignExecutionState(campaign.id) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    override suspend fun doInit(): List<Directive> {
        // Creates one directive by factory to notify its assignments.
        return campaign.factories.map { (factoryId, configuration) ->
            val factory = campaign.factories[factoryId]!!
            FactoryAssignmentDirective(
                campaign.id,
                configuration.assignment,
                idGenerator.short(),
                factory.unicastChannel
            )
        }
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState {
        if (feedback is FactoryAssignmentFeedback) {
            if (feedback.status == FeedbackStatus.IGNORED) {
                campaign.unassignFactory(feedback.nodeId)
                factoryService.releaseFactories(campaign, listOf(feedback.nodeId))
            } else if (feedback.status == FeedbackStatus.FAILED) {
                // The failure management is let to doProcess.
                log.error { "The assignment of DAGs to the factory ${feedback.nodeId} failed: ${feedback.error}" }
            }
        }
        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState {
        return if (feedback is FactoryAssignmentFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                FailureState(campaign, feedback.error ?: "")
            } else {
                expectedFeedbacks -= feedback.nodeId
                if (expectedFeedbacks.isEmpty()) {
                    MinionsAssignmentState(campaign)
                } else {
                    this
                }
            }
        } else {
            this
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}