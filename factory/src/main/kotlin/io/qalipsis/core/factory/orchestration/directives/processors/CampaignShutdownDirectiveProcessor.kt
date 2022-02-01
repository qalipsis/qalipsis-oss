package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
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
internal class CampaignShutdownDirectiveProcessor(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val idGenerator: IdGenerator
) : DirectiveProcessor<CampaignShutdownDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is CampaignShutdownDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: CampaignShutdownDirective) {
        val feedback = CampaignShutdownFeedback(
            key = idGenerator.short(),
            campaignId = directive.campaignId,
            nodeId = factoryCampaignManager.feedbackNodeId,
            status = FeedbackStatus.IN_PROGRESS
        )
        feedbackFactoryChannel.publish(feedback)
        try {
            factoryCampaignManager.shutdownCampaign(directive.campaignId)
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
