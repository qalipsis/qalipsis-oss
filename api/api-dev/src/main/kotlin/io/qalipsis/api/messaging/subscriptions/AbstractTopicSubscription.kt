package io.qalipsis.api.messaging.subscriptions

import io.qalipsis.api.messaging.CancelledSubscriptionException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
internal abstract class AbstractTopicSubscription<T>(
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
    protected val pollNotificationChannel: Channel<Unit>?

    /**
     * Channel to activate the timeout when nothing happened after a poll.
     */
    private var timeout: ReceiveChannel<Unit>?

    /**
     * Coroutine running to consumer and perform the actions from the [onReceiveValue] method.
     */
    private var receivingJob: Job? = null

    init {
        if (idleTimeout.toMillis() > 0) {
            pollNotificationChannel = Channel<Unit>(1)
            timeout = buildTimeout()
            GlobalScope.launch {
                while (active) {
                    select<Unit> {
                        pollNotificationChannel.onReceive {
                            timeout!!.cancel()
                            timeout = buildTimeout()
                        }
                        timeout!!.onReceive {
                            cancel()
                        }
                    }
                }
            }
        } else {
            pollNotificationChannel = null
            timeout = null
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun buildTimeout() = ticker(idleTimeout.toMillis())

    override fun isActive() = active

    override suspend fun onReceiveValue(block: suspend (T) -> Unit): Job {
        verifyState()
        receivingJob?.cancelAndJoin()
        receivingJob = GlobalScope.launch {
            while (active) {
                block(pollValue())
            }
        }
        return receivingJob!!
    }

    override fun cancel() {
        active = false
        cancellation()
        receivingJob?.cancel()
        timeout?.cancel()
        pollNotificationChannel?.close()
    }

    protected fun verifyState() {
        if (!active) {
            throw CancelledSubscriptionException()
        }
    }
}
