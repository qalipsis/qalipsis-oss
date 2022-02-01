package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.head.campaign.CampaignConfiguration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal open class MinionsAssignmentState(
    protected val campaign: CampaignConfiguration
) : AbstractCampaignExecutionState(campaign.id) {

    private val expectedFeedbacks =
        ConcurrentHashMap(campaign.factories.mapValues { it.value.assignment.keys.toSet() })

    private val mutex = Mutex(false)

    override suspend fun doInit(): List<Directive> {
        return campaign.scenarios.map { (scenarioId, config) ->
            MinionsDeclarationDirective(
                campaignId,
                scenarioId,
                config.minionsCount,
                channel = campaign.broadcastChannel,
                key = idGenerator.short()
            )
        }
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState {
        if (feedback is MinionsDeclarationFeedback) {
            if (feedback.status == FeedbackStatus.FAILED) {
                // The failure management is let to doProcess.
                log.error { "The declaration of minions failed in the factory ${feedback.nodeId}: ${feedback.error}" }
            }
        } else if (feedback is MinionsAssignmentFeedback) {
            if (feedback.status == FeedbackStatus.IGNORED) {
                campaign.unassignScenarioOfFactory(feedback.scenarioId, feedback.nodeId)
                if (feedback.nodeId !in campaign) {
                    factoryService.releaseFactories(campaign, listOf(feedback.nodeId))
                }
            } else if (feedback.status == FeedbackStatus.FAILED) {
                // The failure management is let to doProcess.
                log.error { "The assignment of minions to the factory ${feedback.nodeId} failed: ${feedback.error}" }
            }
        }
        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState {
        return if (feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED) {
            FailureState(campaign, feedback.error ?: "")
        } else if (feedback is MinionsAssignmentFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                FailureState(campaign, feedback.error ?: "")
            } else {
                mutex.withLock {
                    expectedFeedbacks.computeIfPresent(feedback.nodeId) { _, scenarios ->
                        (scenarios - feedback.scenarioId).ifEmpty { null }
                    }
                    if (expectedFeedbacks.isEmpty()) {
                        WarmupState(campaign)
                    } else {
                        this
                    }
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