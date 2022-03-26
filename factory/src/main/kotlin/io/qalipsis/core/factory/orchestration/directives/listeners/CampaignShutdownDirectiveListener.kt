package io.qalipsis.core.factory.orchestration.directives.listeners

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Consumes the [CampaignShutdownDirective] to shutdown all the components related to the campaign.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignShutdownDirectiveListener(
    private val factoryCampaignManager: FactoryCampaignManager,
    private val factoryChannel: FactoryChannel,
    private val campaignLifeCycleAwares: Collection<CampaignLifeCycleAware>,
) : DirectiveListener<CampaignShutdownDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is CampaignShutdownDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: CampaignShutdownDirective) {
        val feedback = CampaignShutdownFeedback(
            campaignId = directive.campaignId,
            status = FeedbackStatus.IN_PROGRESS
        )
        factoryChannel.publishFeedback(feedback)
        try {
            val campaign = factoryCampaignManager.runningCampaign
            campaignLifeCycleAwares.forEach { it.close(campaign) }
            factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
        } catch (e: Exception) {
            log.error(e) { e.message }
            factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.FAILED, error = e.message))
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
