/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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