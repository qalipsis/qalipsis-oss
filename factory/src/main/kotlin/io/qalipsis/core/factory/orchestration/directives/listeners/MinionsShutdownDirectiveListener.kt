package io.qalipsis.core.factory.orchestration.directives.listeners

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionsKeeper
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
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class MinionsShutdownDirectiveListener(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val minionsKeeper: MinionsKeeper,
    private val factoryChannel: FactoryChannel
) : DirectiveListener<MinionsShutdownDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsShutdownDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId, directive.scenarioId)
                && directive.minionIds.any(minionsKeeper::contains)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsShutdownDirective) {
        val relevantMinions = directive.minionIds.filter(minionsKeeper::contains)
        if (relevantMinions.isNotEmpty()) {
            val feedback = MinionsShutdownFeedback(
                campaignId = directive.campaignId,
                scenarioId = directive.scenarioId,
                minionIds = relevantMinions,
                status = FeedbackStatus.IN_PROGRESS
            )
            factoryChannel.publishFeedback(feedback)
            tryAndLogOrNull(log) {
                factoryCampaignManager.shutdownMinions(directive.campaignId, relevantMinions)
            }
            factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
        }
    }

    private companion object {
        val log = logger()
    }
}
