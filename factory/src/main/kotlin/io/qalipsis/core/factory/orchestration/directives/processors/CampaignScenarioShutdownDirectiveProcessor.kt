package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignScenarioShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Consumes the [CampaignShutdownDirective] to shutdown all the components of the
 *
 * @author Eric Jessé
 */
@Singleton
internal class CampaignScenarioShutdownDirectiveProcessor(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<CampaignScenarioShutdownDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is CampaignScenarioShutdownDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId, directive.scenarioId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: CampaignScenarioShutdownDirective) {
        val feedback = CampaignScenarioShutdownFeedback(
            key = idGenerator.short(),
            campaignId = directive.campaignId,
            scenarioId = directive.scenarioId,
            nodeId = factoryCampaignManager.feedbackNodeId,
            status = FeedbackStatus.IN_PROGRESS
        )
        feedbackFactoryChannel.publish(feedback)
        try {
            factoryCampaignManager.shutdownScenario(directive.campaignId, directive.scenarioId)
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
