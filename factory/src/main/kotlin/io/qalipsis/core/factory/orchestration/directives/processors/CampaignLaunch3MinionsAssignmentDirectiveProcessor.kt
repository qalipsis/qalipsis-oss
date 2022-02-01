package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

@Singleton
internal class CampaignLaunch3MinionsAssignmentDirectiveProcessor(
    private val minionsKeeper: MinionsKeeper,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val factoryCampaignManager: FactoryCampaignManager,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<MinionsAssignmentDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsAssignmentDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId, directive.scenarioId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: MinionsAssignmentDirective) {
        val feedback = MinionsAssignmentFeedback(
            key = idGenerator.short(),
            campaignId = directive.campaignId,
            scenarioId = directive.scenarioId,
            nodeId = factoryCampaignManager.feedbackNodeId,
            status = FeedbackStatus.IN_PROGRESS
        )
        feedbackFactoryChannel.publish(feedback)
        try {
            val assignedMinions = minionAssignmentKeeper.assign(directive.campaignId, directive.scenarioId)
            if (assignedMinions.isEmpty()) {
                feedbackFactoryChannel.publish(
                    feedback.copy(
                        key = idGenerator.short(),
                        status = FeedbackStatus.IGNORED
                    )
                )
            } else {
                assignedMinions.forEach { (minionId, dags) ->
                    minionsKeeper.create(directive.campaignId, directive.scenarioId, dags, minionId)
                }
                feedbackFactoryChannel.publish(
                    feedback.copy(
                        key = idGenerator.short(),
                        status = FeedbackStatus.COMPLETED
                    )
                )
            }
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
