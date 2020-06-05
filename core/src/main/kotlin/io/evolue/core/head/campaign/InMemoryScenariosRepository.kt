package io.evolue.core.head.campaign

import io.evolue.api.context.ScenarioId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedback
import io.evolue.core.cross.driving.feedback.FeedbackConsumer
import io.evolue.core.head.persistence.inmemory.InMemoryRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Component to store and provides scenarios from all the factories.
 * The component automatically consumes the feedback from the factories to store all the scenarios.
 *
 * @author Eric Jessé
 */
internal class InMemoryScenariosRepository(
    private val feedbackConsumer: FeedbackConsumer
) : InMemoryRepository<HeadScenario, ScenarioId>(), HeadScenarioRepository {

    init {
        val rep = this
        GlobalScope.launch {
            feedbackConsumer.subscribe().collect { feedback ->
                when (feedback) {
                    is FactoryRegistrationFeedback -> {
                        log.info("Persisting scenarios ${feedback.scenarios.joinToString(", ") { it.id }}")
                        rep.saveAll(feedback.scenarios)
                    }
                }
            }
        }
    }

    companion object {
        private val log = logger()
    }
}
