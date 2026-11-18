/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.messaging.subscriptions

import io.qalipsis.api.coroutines.newCoroutineScope
import io.qalipsis.api.messaging.CancelledSubscriptionException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 *
 * @author Eric Jessé
 */
abstract class AbstractTopicSubscription<T>(
    private val subscriberId: String,
    private val idleTimeout: Duration,
    private val cancellation: (() -> Unit)
) : TopicSubscription<T> {

    /**
     * Indicates if the subscription is active or not.
     */
    @Volatile
    protected var active = true

    /**
     * Tracks the deadline for the next idle timeout check.
     * Updated by subclasses after receiving a value via [resetIdleDeadline].
     */
    private val idleDeadline = AtomicLong(0)

    /**
     * Coroutine running to consume and perform the actions from the [onReceiveValue] method.
     */
    private var receivingJob: Job? = null

    private var idleVerificationJob: Job? = null

    protected suspend fun init() {
        val idleTimeoutMs = idleTimeout.toMillis()
        if (idleTimeoutMs > 0) {
            resetIdleDeadline()
            idleVerificationJob = newCoroutineScope().launch {
                try {
                    log.trace { "Subscription $subscriberId: Idle verification loop started with a timeout of $idleTimeout" }
                    while (active) {
                        val remaining = idleDeadline.get() - System.currentTimeMillis()
                        if (remaining <= 0) {
                            log.trace { "Subscription $subscriberId: Idle timeout reached" }
                            cancel()
                        } else {
                            delay(remaining)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors.
                }
            }
            log.trace { "Subscription $subscriberId: Idle verification loop created with a timeout of $idleTimeout" }
        }
    }

    /**
     * Resets the idle deadline. Called by subclasses after successfully receiving a record from [poll].
     */
    protected fun resetIdleDeadline() {
        idleDeadline.set(System.currentTimeMillis() + idleTimeout.toMillis())
    }

    override fun isActive() = active

    override suspend fun onReceiveValue(block: suspend (T) -> Unit): Job {
        verifyState()
        receivingJob?.cancelAndJoin()
        receivingJob = newCoroutineScope().launch {
            log.trace { "Subscription $subscriberId: Receiving loop started for ${this@AbstractTopicSubscription}" }
            while (active) {
                block(pollValue())
            }
            log.trace { "Subscription $subscriberId: Receiving loop completed for ${this@AbstractTopicSubscription}" }
        }
        return receivingJob!!
    }

    override fun cancel() {
        log.trace { "Subscription $subscriberId: Cancelling the subscription $this" }
        active = false
        idleVerificationJob?.cancel()
        cancellation()
        receivingJob?.cancel()
    }

    internal suspend fun waitForCompletion() {
        idleVerificationJob?.join()
        receivingJob?.join()
    }

    protected fun verifyState() {
        log.trace { "Verifying state for subscription $subscriberId: $active" }
        if (!active) {
            throw CancelledSubscriptionException()
        }
    }

    override fun toString(): String {
        return "${this::class.simpleName}(subscriberId='$subscriberId', active=$active)"
    }

    private companion object {

        @JvmStatic
        val log = KotlinLogging.logger { }

    }
}
