package io.qalipsis.core.heads.campaigns

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.annotations.VisibleForTest
import io.qalipsis.api.heads.StartupHeadComponent
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.cross.configuration.ENV_AUTOSTART
import io.qalipsis.core.cross.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.heads.lifetime.ProcessBlocker
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
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
    private val headScenarioRepository: HeadScenarioRepository
) : ProcessBlocker, StartupHeadComponent {

    private val latch = SuspendedCountLatch(1)

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
                            headScenarioRepository.saveAll(feedback.scenarios)
                            latch.reset()
                            campaignManager.start(
                                    scenarios = feedback.scenarios.map { it.id }) { message ->
                                onCriticalFailure(message)
                            }
                        } else {
                            log.error("No executable scenario was found")
                            latch.release()
                        }
                    }
                    is EndOfCampaignFeedback -> {
                        latch.release()
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
        latch.await()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
