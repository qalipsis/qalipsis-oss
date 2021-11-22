package io.qalipsis.core.heads.campaigns

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.heads.StartupHeadComponent
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.annotations.lifetime.ProcessBlocker
import io.qalipsis.core.configuration.ExecutionEnvironments.AUTOSTART
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.FactoryRegistrationFeedback
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.event.Level
import javax.annotation.PreDestroy
import kotlin.system.exitProcess

/**
 * Component to automatically starts the execution of a campaign with all the scenarios as soon as
 * a [FactoryRegistrationFeedback] is started.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [AUTOSTART])
internal class CampaignAutoStarter(
    private val feedbackConsumer: FeedbackConsumer,
    private val campaignManager: CampaignManager,
    private val campaignStateKeeper: CampaignStateKeeper,
    private val scenarioSummaryRepository: ScenarioSummaryRepository,
    private val eventsLogger: EventsLogger,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val executionCoroutineScope: CoroutineScope,
    @Property(name = "campaign.name") private val campaignName: String
) : ProcessBlocker, StartupHeadComponent {

    @KTestable
    private val runningScenariosLatch = SuspendedCountLatch()

    private var consumptionJob: Job? = null

    override fun getStartupOrder() = Int.MIN_VALUE

    override fun init() {
        executionCoroutineScope.launch {
            log.debug { "Consuming from $feedbackConsumer" }
            consumptionJob = feedbackConsumer.onReceive("${this@CampaignAutoStarter::class.simpleName}") { feedback ->
                receivedFeedback(feedback)
            }
        }
    }

    @LogInputAndOutput(level = Level.DEBUG)
    protected suspend fun receivedFeedback(feedback: Feedback) {
        when (feedback) {
            is FactoryRegistrationFeedback -> {
                if (feedback.scenarios.isNotEmpty()) {
                    log.info { "The scenario(s) ${feedback.scenarios.joinToString { it.id }} is(are) ready to start" }
                    eventsLogger.start()
                    scenarioSummaryRepository.saveAll(feedback.scenarios)
                    runningScenariosLatch.increment(feedback.scenarios.size.toLong())
                    log.info { "Triggering the campaign $campaignName for the scenario(s) ${feedback.scenarios.joinToString { it.id }}" }
                    campaignManager.start(
                        id = campaignName,
                        scenarios = feedback.scenarios.map { it.id }) { message ->
                        onCriticalFailure(message)
                    }
                } else {
                    log.error { "No executable scenario was found" }
                    campaignStateKeeper.abort(campaignName)
                    // Increments in order to release the awaitActivity().
                    runningScenariosLatch.increment()
                    runningScenariosLatch.release()
                }
            }
            is EndOfCampaignFeedback -> {
                // Ensure that the latch was incremented before it is decremented, in case the scenario is too fast.
                runningScenariosLatch.awaitActivity()
                log.info { "The campaign ${feedback.campaignId} of scenario ${feedback.scenarioId} was completed" }
                runningScenariosLatch.decrement()
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
    private fun onCriticalFailure(message: String) {
        log.error { message }
        System.err.println("An error occurred that requires the program to exit.")
        System.err.println(message)
        exitProcess(1)
    }

    override suspend fun join() {
        runningScenariosLatch.awaitActivity()
        log.info { "Waiting for the ${runningScenariosLatch.get()} scenario(s) to be completed" }
        runningScenariosLatch.await()
        log.info { "Stopping the events logger" }
        eventsLogger.stop()
        log.info { "The events logger was stopped" }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
