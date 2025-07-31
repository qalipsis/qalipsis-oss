/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.steps.topicrelatedsteps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.CancelledSubscriptionException
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.subscriptions.TopicSubscription
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.factory.context.StepContextImpl
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
import io.qalipsis.core.factory.steps.MinionsKeeperAware
import io.qalipsis.core.factory.steps.RunnerAware
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Step forcing the execution of next step after a record was received from [topic].
 *
 * @property parentStepName step providing data to the current one via the topic
 * @property topic topic to receive data from the "parent" step
 * @property minionsKeeper provides the minions and information about them
 * @property runner executes a minion on a step, using a new context
 * @property filter specification to filter the record from the remote step and let them go forward or not, default to always true
 *
 * @author Eric Jessé
 */
class TopicDataPushStep<I>(
    id: StepName,
    private val parentStepName: StepName,
    private val topic: Topic<I>,
    private val filter: (suspend (remoteRecord: I) -> Boolean) = { _ -> true },
    private val coroutineScope: CoroutineScope
) : AbstractStep<I, I>(id, null), RunnerAware, MinionsKeeperAware {

    override lateinit var minionsKeeper: MinionsKeeper

    override lateinit var runner: Runner

    private var running = false

    private lateinit var topicSubscription: TopicSubscription<I>

    private lateinit var consumingJob: Job

    override suspend fun start(context: StepStartStopContext) {
        running = true
        val nextStep = next.first()
        val stepName = nextStep.name
        val minion = minionsKeeper.getSingletonMinion(context.scenarioName, context.dagId)
        log.debug { "Starting to push data with the minion $minion" }

        // Starts the coroutines that consumes the topic and pass the values to the step after.
        consumingJob = coroutineScope.launch {
            topicSubscription = topic.subscribe(nextStep.name)
            try {
                while (running) {
                    val valueFromTopic = topicSubscription.pollValue()
                    if (filter(valueFromTopic)) {
                        val ctx = StepContextImpl<I, I>(
                            input = Channel<I>(1).also { it.send(valueFromTopic) },
                            campaignKey = context.campaignKey,
                            scenarioName = context.scenarioName,
                            previousStepName = parentStepName,
                            stepName = stepName,
                            minionId = minion.id,
                            isTail = false // We actually never know when the tail will come.
                        )
                        log.debug { "Pushing the value $valueFromTopic" }
                        runner.runMinion(minion, nextStep, ctx)
                    }
                }
            } catch (e: CancellationException) {
                // Ignore.
            } catch (e: CancelledSubscriptionException) {
                // Ignore.
            } catch (e: Exception) {
                log.warn(e) { e.message }
            } finally {
                topicSubscription.cancel()
            }
        }
        super.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        running = false
        kotlin.runCatching {
            consumingJob.cancelAndJoin()
        }
        topicSubscription.cancel()
        super.stop(context)
    }

    override suspend fun destroy() {
        topic.close()
    }

    override suspend fun execute(context: StepContext<I, I>) {
        // Ignore the execution.
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
