package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsShutdownFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Consumes the [MinionsShutdownDirective] to shutdown all the components of the specified minions.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinionsShutdownDirectiveProcessor(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val minionsKeeper: MinionsKeeper,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<MinionsShutdownDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsShutdownDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId, directive.scenarioId)
                && directive.minionIds.any(minionsKeeper::contains)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: MinionsShutdownDirective) {
        val relevantMinions = directive.minionIds.filter(minionsKeeper::contains)
        if (relevantMinions.isNotEmpty()) {
            val feedback = MinionsShutdownFeedback(
                key = idGenerator.short(),
                campaignId = directive.campaignId,
                scenarioId = directive.scenarioId,
                minionIds = relevantMinions,
                nodeId = factoryCampaignManager.feedbackNodeId,
                status = FeedbackStatus.IN_PROGRESS
            )
            feedbackFactoryChannel.publish(feedback)
            runCatching {
                factoryCampaignManager.shutdownMinions(directive.campaignId, relevantMinions)
            }
            feedbackFactoryChannel.publish(feedback.copy(key = idGenerator.short(), status = FeedbackStatus.COMPLETED))
        }
    }
}
