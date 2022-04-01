package io.qalipsis.core.factory.orchestration.directives.listeners

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * The [CampaignLaunch1FactoryAssignmentDirectiveListener] is responsible for saving the assignment of the DAGS
 * into the current factory for the starting scenario.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch1FactoryAssignmentDirectiveListener(
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val campaignLifeCycleAwares: Collection<CampaignLifeCycleAware>,
    private val factoryChannel: FactoryChannel
) : DirectiveListener<FactoryAssignmentDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is FactoryAssignmentDirective
    }

    @LogInputAndOutput(level = Level.DEBUG)
    override suspend fun notify(directive: FactoryAssignmentDirective) {
        val feedback = FactoryAssignmentFeedback(
            campaignName = directive.campaignName,
            status = FeedbackStatus.IN_PROGRESS
        )
        try {
            val campaign = Campaign(
                campaignName = directive.campaignName,
                broadcastChannel = directive.broadcastChannel,
                feedbackChannel = directive.feedbackChannel,
                assignments = directive.assignments
            )
            campaignLifeCycleAwares.forEach {
                it.init(campaign)
            }
            factoryChannel.publishFeedback(feedback)
            minionAssignmentKeeper.assignFactoryDags(directive.campaignName, directive.assignments)
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