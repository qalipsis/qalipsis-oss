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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Duration

/**
 * Decorator of a step, which generates an error with the timeout is reached before the end of the operation.
 *
 * @author Eric Jessé
 */
internal class TimeoutStepDecorator<I, O>(
    private val timeout: Duration,
    override val decorated: Step<I, O>,
    private val meterRegistry: CampaignMeterRegistry
) : Step<I, O>, StepExecutor, StepDecorator<I, O> {

    override val name: StepName
        get() = decorated.name

    override var retryPolicy: RetryPolicy? = null

    override var next = decorated.next

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        try {
            withTimeout(timeout.toMillis()) {
                executeStep(minion, decorated, context)
            }
        } catch (e: TimeoutCancellationException) {
            meterRegistry.counter("step-${name}-timeout", "minion", context.minionId).increment()
            context.isExhausted = true
            throw e
        }
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // This method should never be called.
        throw NotImplementedError()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }


}
