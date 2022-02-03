package io.qalipsis.api.messaging.subscriptions

import io.qalipsis.api.coroutines.newCoroutineScope
import io.qalipsis.api.messaging.CancelledSubscriptionException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.time.Duration
import kotlin.coroutines.coroutineContext

/**
 *
 * @author Eric Jessé
 */
internal abstract class AbstractTopicSubscription<T>(
    private val subscriberId: String,
    private val idleTimeout: Duration,
    private val cancellation: (() -> Unit)
) : TopicSubscription<T> {

    /**
     * Indicates if the subscription is active or not.
     */
    protected var active = true

    /**
     * Channel to verify that the timeout is not reached between two polls.
     */
    protected var pollNotificationChannel: Channel<Unit>? = null

    /**
     * Channel to activate the timeout when nothing happened after a poll.
     */
    private var timeout: ReceiveChannel<Unit>? = null

    /**
     * Coroutine running to consumer and perform the actions from the [onReceiveValue] method.
     */
    private var receivingJob: Job? = null

    private var idleVerificationJob: Job? = null

    protected suspend fun init() {
        if (idleTimeout.toMillis() > 0) {
            pollNotificationChannel = Channel(1)
            timeout = buildTimeout()
            coroutineContext
            idleVerificationJob = newCoroutineScope().launch {
                try {
                    log.trace { "Subscription ${subscriberId}: Idle verification loop started for ${this@AbstractTopicSubscription} with a timeout of $idleTimeout" }
                    while (active) {
                        select<Unit> {
                            pollNotificationChannel!!.onReceive {
                                timeout!!.cancel()
                                timeout = buildTimeout()
                            }
                            timeout!!.onReceive {
                                log.trace { "Subscription ${subscriberId}: Idle timeout reached for the subscription ${this@AbstractTopicSubscription}" }
                                cancel()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors.
                }
            }
            log.trace { "Subscription ${subscriberId}: Idle verification loop created for ${this@AbstractTopicSubscription} with a timeout of $idleTimeout" }
        }
    }

    private fun buildTimeout() = ticker(idleTimeout.toMillis())

    override fun isActive() = active

    override suspend fun onReceiveValue(block: suspend (T) -> Unit): Job {
        verifyState()
        receivingJob?.cancelAndJoin()
        receivingJob = newCoroutineScope().launch {
            log.trace { "Subscription ${subscriberId}: Receiving loop started for ${this@AbstractTopicSubscription}" }
            while (active) {
                block(pollValue())
            }
            log.trace { "Subscription ${subscriberId}: Receiving loop completed for ${this@AbstractTopicSubscription}" }
        }
        return receivingJob!!
    }

    override fun cancel() {
        log.trace { "Subscription ${subscriberId}: Cancelling the subscription $this" }
        active = false
        idleVerificationJob?.cancel()
        cancellation()
        receivingJob?.cancel()
        timeout?.cancel()
        pollNotificationChannel?.close()
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
