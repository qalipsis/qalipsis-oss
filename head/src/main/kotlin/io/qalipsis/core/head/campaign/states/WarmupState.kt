package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal open class WarmupState(
    protected val campaign: CampaignConfiguration
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.id) {

    private val expectedFeedbacks =
        ConcurrentHashMap(campaign.factories.mapValues { it.value.assignment.keys.toSet() })

    private val mutex = Mutex(false)

    override suspend fun doInit(): List<Directive> {
        return campaign.factories.values.flatMap { config ->
            config.assignment.keys.map { scenarioId ->
                ScenarioWarmUpDirective(
                    campaignId = campaignId,
                    scenarioId = scenarioId,
                    channel = config.unicastChannel
                )
            }
        }
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        if (feedback is ScenarioWarmUpFeedback) {
            if (feedback.status == FeedbackStatus.IGNORED) {
                campaign.unassignScenarioOfFactory(feedback.scenarioId, feedback.nodeId)
                if (feedback.nodeId !in campaign) {
                    context.factoryService.releaseFactories(campaign, listOf(feedback.nodeId))
                }
            } else if (feedback.status == FeedbackStatus.FAILED) {
                // The failure management is let to doProcess.
                log.error { "The assignment of minions to the factory ${feedback.nodeId} failed: ${feedback.error}" }
            }
        }
        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is ScenarioWarmUpFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                FailureState(campaign, feedback.error ?: "")
            } else {
                mutex.withLock {
                    expectedFeedbacks.computeIfPresent(feedback.nodeId) { _, scenarios ->
                        (scenarios - feedback.scenarioId).ifEmpty { null }
                    }
                    if (expectedFeedbacks.isEmpty()) {
                        MinionsStartupState(campaign)
                    } else {
                        this
                    }
                }
            }
        } else {
            this
        }
    }

    override fun toString(): String {
        return "WarmupState(campaign=$campaign, expectedFeedbacks=$expectedFeedbacks)"
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}