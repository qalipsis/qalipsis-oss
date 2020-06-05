package io.evolue.core.head.campaign

import com.google.common.annotations.VisibleForTesting
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedback
import io.evolue.core.cross.driving.feedback.FeedbackConsumer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Component to automatically starts the execution of a campaign with all the scenarios as soon as
 * a [FactoryRegistrationFeedback] is started.
 *
 * @author Eric Jessé
 */
internal class CampaignAutoStarter(
    private val feedbackConsumer: FeedbackConsumer,
    private val campaignManager: CampaignManager
) {

    init {
        GlobalScope.launch {
            feedbackConsumer.subscribe().collect { feedback ->
                when (feedback) {
                    is FactoryRegistrationFeedback -> campaignManager.start(
                        scenarios = feedback.scenarios.map { it.id }) { message ->
                        onCriticalFailure(message)
                    }
                }
            }
        }
    }

    /**
     * Log the error and quit the program.
     */
    @VisibleForTesting
    internal fun onCriticalFailure(message: String) {
        log.error(message)
        System.err.println("An error occurred that requires the program to exit.")
        System.err.println(message)
        exitProcess(1)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
