package io.qalipsis.core.factory.orchestration.directives.listeners

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsRampUpPreparationDirectiveReference
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 *
 * The [CampaignLaunch5MinionsRampUpPreparationDirectiveListener] is responsible for generating the ramp-up strategy to start all
 * the [io.qalipsis.api.orchestration.Minion]s for the execution of a scenario.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class CampaignLaunch5MinionsRampUpPreparationDirectiveListener(
    private val scenarioRegistry: ScenarioRegistry,
    private val factoryChannel: FactoryChannel,
    private val factoryCampaignManager: FactoryCampaignManager
) : DirectiveListener<MinionsRampUpPreparationDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsRampUpPreparationDirectiveReference
                && factoryCampaignManager.isLocallyExecuted(directive.campaignKey, directive.scenarioName)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun notify(directive: MinionsRampUpPreparationDirective) {
        val feedback = MinionsRampUpPreparationFeedback(
            campaignKey = directive.campaignKey,
            scenarioName = directive.scenarioName,
            status = FeedbackStatus.IN_PROGRESS
        )
        factoryChannel.publishFeedback(feedback)
        try {
            val scenario = scenarioRegistry[directive.scenarioName]!!
            val minionsStartDefinitions = factoryCampaignManager.prepareMinionsRampUp(
                directive.campaignKey, scenario, directive.rampUpConfiguration
            )

            minionsStartDefinitions.windowed(400, 400, true).forEach { def ->
                factoryChannel.publishDirective(
                    MinionsStartDirective(
                        directive.campaignKey,
                        scenario.name,
                        def,
                    )
                )
            }

            factoryChannel.publishFeedback(
                feedback.copy(
                    status = FeedbackStatus.COMPLETED
                )
            )
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
