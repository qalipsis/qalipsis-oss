package io.qalipsis.core.factory.orchestration.directives.listeners

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch3MinionsAssignmentDirectiveListener(
    private val minionsKeeper: MinionsKeeper,
    private val minionAssignmentKeeper: MinionAssignmentKeeper,
    private val factoryChannel: FactoryChannel,
    private val factoryCampaignManager: FactoryCampaignManager
) : DirectiveListener<MinionsAssignmentDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsAssignmentDirective
                && factoryCampaignManager.isLocallyExecuted(directive.campaignName, directive.scenarioName)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsAssignmentDirective) {
        val feedback = MinionsAssignmentFeedback(
            campaignName = directive.campaignName,
            scenarioName = directive.scenarioName,
            status = FeedbackStatus.IN_PROGRESS
        )
        factoryChannel.publishFeedback(feedback)
        try {
            val assignedMinions = minionAssignmentKeeper.assign(directive.campaignName, directive.scenarioName)
            if (assignedMinions.isEmpty()) {
                factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.IGNORED))
            } else {
                assignedMinions.forEach { (minionId, dags) ->
                    minionsKeeper.create(directive.campaignName, directive.scenarioName, dags, minionId)
                }
                factoryChannel.publishFeedback(feedback.copy(status = FeedbackStatus.COMPLETED))
            }
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
