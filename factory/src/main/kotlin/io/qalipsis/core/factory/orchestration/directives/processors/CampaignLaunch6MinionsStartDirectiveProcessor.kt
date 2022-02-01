package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level
import java.time.Instant

/**
 * Consumes the MinionsStartDirective and schedules the start of the related minions if they are under load and have
 * their roots in the local factory.
 *
 * @author Eric Jessé
 */
@Singleton
internal class CampaignLaunch6MinionsStartDirectiveProcessor(
    private val localAssignmentStore: LocalAssignmentStore,
    private val minionsKeeper: MinionsKeeper,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val factoryCampaignManager: FactoryCampaignManager,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<MinionsStartDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsStartDirective && localAssignmentStore.hasMinionsAssigned(directive.scenarioId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: MinionsStartDirective) {
        try {
            val relevantMinions = directive.startDefinitions.filter {
                localAssignmentStore.hasRootUnderLoadLocally(directive.scenarioId, it.minionId)
            }
            if (relevantMinions.isNotEmpty()) {
                val feedback = MinionsStartFeedback(
                    key = idGenerator.short(),
                    campaignId = directive.campaignId,
                    scenarioId = directive.scenarioId,
                    nodeId = factoryCampaignManager.feedbackNodeId,
                    status = FeedbackStatus.IN_PROGRESS
                )
                feedbackFactoryChannel.publish(feedback)
                relevantMinions.forEach {
                    minionsKeeper.scheduleMinionStart(it.minionId, Instant.ofEpochMilli(it.timestamp))
                }
                feedbackFactoryChannel.publish(
                    feedback.copy(
                        key = idGenerator.short(),
                        status = FeedbackStatus.COMPLETED
                    )
                )
            } else {
                val feedback = MinionsStartFeedback(
                    key = idGenerator.short(),
                    campaignId = directive.campaignId,
                    scenarioId = directive.scenarioId,
                    nodeId = factoryCampaignManager.feedbackNodeId,
                    status = FeedbackStatus.IGNORED
                )
                feedbackFactoryChannel.publish(feedback)
            }
        } catch (e: Exception) {
            log.error(e) { e.message }
            feedbackFactoryChannel.publish(
                MinionsStartFeedback(
                    key = idGenerator.short(),
                    campaignId = directive.campaignId,
                    scenarioId = directive.scenarioId,
                    nodeId = factoryCampaignManager.feedbackNodeId,
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
