package io.qalipsis.core.heads.campaigns

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.annotations.VisibleForTest
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.heads.StartupHeadComponent
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.cross.configuration.ENV_AUTOSTART
import io.qalipsis.core.cross.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.heads.lifetime.ProcessBlocker
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import javax.annotation.Nullable
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Component to automatically starts the execution of a campaign with all the scenarios as soon as
 * a [FactoryRegistrationFeedback] is started.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_AUTOSTART])
internal class CampaignAutoStarter(
    private val feedbackConsumer: FeedbackConsumer,
    private val campaignManager: CampaignManager,
    private val headScenarioRepository: HeadScenarioRepository,
    private val eventsLogger: EventsLogger,
    @Property(name = "campaign.name") private val campaignName: String
) : ProcessBlocker, StartupHeadComponent {

    private val runningScenariosLatch = SuspendedCountLatch()

    private var consumptionJob: Job? = null

    @PostConstruct
    fun init() {
        runBlocking {
            log.debug("Consuming from $feedbackConsumer")
            consumptionJob = feedbackConsumer.onReceive { feedback ->
                log.debug("Received feedback $feedback")
                when (feedback) {
                    is FactoryRegistrationFeedback -> {
                        if (feedback.scenarios.isNotEmpty()) {
                            runningScenariosLatch.increment(feedback.scenarios.size.toLong())
                            eventsLogger.start()
                            headScenarioRepository.saveAll(feedback.scenarios)
                            campaignManager.start(
                                id = campaignName,
                                scenarios = feedback.scenarios.map { it.id }) { message ->
                                onCriticalFailure(message)
                            }
                        } else {
                            log.error("No executable scenario was found")
                            // Increments in order to release the awaitActivity().
                            runningScenariosLatch.increment()
                            runningScenariosLatch.release()
                        }
                    }
                    is EndOfCampaignFeedback -> {
                        log.info("The campaign ${feedback.campaignId} of scenario ${feedback.scenarioId} was completed")
                        runningScenariosLatch.decrement()
                    }
                }
            }
        }
    }

    @PreDestroy
    fun destroy() {
        consumptionJob?.cancel()
    }

    /**
     * Log the error and quit the program.
     */
    @VisibleForTest
    internal fun onCriticalFailure(message: String) {
        log.error(message)
        System.err.println("An error occurred that requires the program to exit.")
        System.err.println(message)
        exitProcess(1)
    }

    override suspend fun join() {
        runningScenariosLatch.awaitActivity()
        log.info("Waiting for the ${runningScenariosLatch.get()} scenario(s) to be completed")
        runningScenariosLatch.await()
        log.info("Stopping the events logger")
        eventsLogger.stop()
        log.info("The events logger was stopped")
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
