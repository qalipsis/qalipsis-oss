package io.qalipsis.core.factories.steps.topicrelatedsteps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.subscriptions.TopicSubscription
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.factories.orchestration.Runner
import io.qalipsis.core.factories.steps.MinionsKeeperAware
import io.qalipsis.core.factories.steps.RunnerAware
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Step forcing the execution of next step after a record was received from [topic].
 *
 * @property parentStepId step providing data to the current one via the topic
 * @property topic topic to receive data from the "parent" step
 * @property minionsKeeper provides the minions and information about them
 * @property runner executes a minion on a step, using a new context
 * @property filter specification to filter the record from the remote step and let them go forward or not, default to always true
 *
 * @author Eric Jessé
 */
internal class TopicDataPushStep<I>(
        id: StepId,
        private val parentStepId: StepId,
        private val topic: Topic<I>,
        private val filter: (suspend (remoteRecord: I) -> Boolean) = { _ -> true }
) : AbstractStep<I, I>(id, null), RunnerAware, MinionsKeeperAware {

    override lateinit var minionsKeeper: MinionsKeeper

    override lateinit var runner: Runner

    private var running = false

    private lateinit var topicSubscription: TopicSubscription<I>

    override suspend fun start(context: StepStartStopContext) {
        running = true
        val nextStep = next.first()
        val stepId = this.id
        val minion = minionsKeeper.getSingletonMinion(context.dagId)
        log.debug("Starting to push data with the minion $minion")

        // Starts the coroutines that consumes the topic to trigger the step after.
        GlobalScope.launch {
            topicSubscription = topic.subscribe(nextStep.id)
            try {
                val valueFromTopic = topicSubscription.pollValue()
                if (filter(valueFromTopic)) {
                    val input = Channel<I>(1)
                    val ctx = StepContext<I, I>(
                            input = input,
                            campaignId = context.campaignId,
                            scenarioId = context.scenarioId,
                            parentStepId = parentStepId,
                            stepId = stepId,
                            directedAcyclicGraphId = context.dagId,
                            minionId = minion.id
                    )
                    input.offer(valueFromTopic)
                    runner.launch(minion, nextStep, ctx)
                }
            } catch (e: Exception) {
                log.warn(e.message, e)
            } finally {
                topicSubscription.cancel()
            }
        }
        super.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        running = false
        topicSubscription.cancel()
        super.stop(context)
    }

    override suspend fun destroy() {
        topic.close()
    }

    override suspend fun execute(context: StepContext<I, I>) {
        throw IllegalAccessException("This method should never be called")
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
