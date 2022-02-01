package io.qalipsis.core.factory.orchestration.directives.processors

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.MinionsRampUpPreparationDirectiveReference
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 *
 * The [CampaignLaunch5MinionsRampUpPreparationDirectiveProcessor] is responsible for generating the ramp-up strategy to start all
 * the [io.qalipsis.api.orchestration.Minion]s for the execution of a scenario.
 *
 * @author Eric Jessé
 */
@Singleton
internal class CampaignLaunch5MinionsRampUpPreparationDirectiveProcessor(
    private val scenarioRegistry: ScenarioRegistry,
    private val directiveProducer: DirectiveProducer,
    private val directiveRegistry: DirectiveRegistry,
    private val feedbackFactoryChannel: FeedbackFactoryChannel,
    private val factoryConfiguration: FactoryConfiguration,
    private val idGenerator: IdGenerator,
    private val factoryCampaignManager: FactoryCampaignManager
) : DirectiveProcessor<MinionsRampUpPreparationDirectiveReference> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsRampUpPreparationDirectiveReference
                && factoryCampaignManager.isLocallyExecuted(directive.campaignId, directive.scenarioId)
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: MinionsRampUpPreparationDirectiveReference) {
        scenarioRegistry[directive.scenarioId]?.let { scenario ->
            directiveRegistry.read(directive)?.let { rampUpConfiguration ->
                val feedback = MinionsRampUpPreparationFeedback(
                    key = idGenerator.short(),
                    campaignId = directive.campaignId,
                    scenarioId = directive.scenarioId,
                    nodeId = factoryCampaignManager.feedbackNodeId,
                    status = FeedbackStatus.IN_PROGRESS
                )
                feedbackFactoryChannel.publish(feedback)
                try {
                    val minionsStartDefinitions = factoryCampaignManager.prepareMinionsRampUp(
                        directive.campaignId, scenario, rampUpConfiguration
                    )
                    directiveProducer.publish(
                        MinionsStartDirective(
                            directive.campaignId,
                            scenario.id,
                            minionsStartDefinitions,
                            channel = factoryConfiguration.directiveRegistry.broadcastDirectivesChannel,
                            key = idGenerator.short()
                        )
                    )
                    feedbackFactoryChannel.publish(
                        feedback.copy(
                            key = idGenerator.short(),
                            status = FeedbackStatus.COMPLETED
                        )
                    )
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
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
