/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.retry

import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.context.StepContext
import kotlinx.coroutines.delay
import java.time.Duration
import javax.validation.constraints.Min

/**
 * Retry policy that allows for limit-based retries when a step fails.
 *
 * @property retries maximum number of attempts on failure
 * @property delay initial delay to wait from a failure to the next attempt
 * @property multiplier factor to multiply the delay between each attempt
 * @property maxDelay maximal duration allowed between two attempts.
 *
 * @author Francisca Eze
 */
class BackoffRetryPolicy(
    var retries: Int = 3,
    @PositiveDuration
    var delay: Duration = Duration.ofSeconds(1),
    @Min(1)
    val multiplier: Double = 1.0,
    @PositiveDuration
    val maxDelay: Duration = Duration.ofHours(1)
) :
    RetryPolicy {
    override suspend fun <I, O> execute(context: StepContext<I, O>, executable: suspend (StepContext<I, O>) -> Unit) {
        var waitTime = delay.toMillis()
        var counter = 0
        do {
            var isSuccess = try {
                executable(context)
                true
            } catch (ex: Exception) {
                delay(waitTime)
                waitTime = (multiplier.toLong() * waitTime).coerceAtMost(maxDelay.toMillis())
                if (counter >= retries) {
                    throw ex
                }
                false
            } finally {
                counter++
            }
        } while (!isSuccess && counter <= retries)
    }
}