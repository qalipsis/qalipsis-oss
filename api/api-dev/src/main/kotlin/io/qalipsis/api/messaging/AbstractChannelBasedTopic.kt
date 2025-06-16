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

package io.qalipsis.api.messaging

import io.qalipsis.api.messaging.subscriptions.ChannelBasedTopicSubscription
import io.qalipsis.api.messaging.subscriptions.TopicSubscription
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 *  Abstract implementation of [Topic] using native capabilities of [kotlinx.coroutines.channels.Channel]s.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractChannelBasedTopic<T>(
    /**
     * Idle time of a subscription. Once a subscription passed this duration without record, it is cancelled.
     */
    private val idleTimeout: Duration

) : Topic<T> {

    private var open = true

    protected abstract val channel: SendChannel<Record<T>>

    protected val subscriptionMutex = Mutex(false)

    protected val subscriptions = ConcurrentHashMap<String, ChannelBasedTopicSubscription<T>>()

    override suspend fun subscribe(subscriberId: String): TopicSubscription<T> {
        verifyState()
        return if (subscriptions.containsKey(subscriberId)) {
            log.trace { "Returning existing subscription for $subscriberId" }
            subscriptions[subscriberId]!!
        } else {
            subscriptionMutex.withLock {
                if (subscriptions.containsKey(subscriberId)) {
                    log.trace { "Returning existing subscription for $subscriberId" }
                    subscriptions[subscriberId]!!
                } else {
                    log.trace { "Creating new subscription for $subscriberId" }
                    val subscriptionChannel = buildSubscriptionChannel()
                    ChannelBasedTopicSubscription.create(subscriberId, subscriptionChannel, idleTimeout) {
                        subscriptions.remove(subscriberId)
                        onSubscriptionCancel(subscriptionChannel)
                    }.also {
                        subscriptions[subscriberId] = it
                        log.trace { "New subscription created for $subscriberId" }
                    }
                }
            }
        }
    }

    protected abstract fun buildSubscriptionChannel(): ReceiveChannel<Record<T>>

    protected abstract fun onSubscriptionCancel(subscriptionChannel: ReceiveChannel<Record<T>>): (() -> Unit)

    override suspend fun produce(record: Record<T>) {
        verifyState()
        log.trace { "Producing record $record" }
        channel.send(record)
    }

    override suspend fun produceValue(value: T) {
        verifyState()
        log.trace { "Producing value $value" }
        produce(Record<T>(value = value))
    }

    override suspend fun poll(subscriberId: String): Record<T> {
        verifyState()
        return subscriptions[subscriberId]?.poll() ?: error("The subscription $subscriberId no longer exists")
    }

    override suspend fun pollValue(subscriberId: String): T {
        verifyState()
        return poll(subscriberId).value
    }

    override fun cancel(subscriberId: String) {
        subscriptions.remove(subscriberId)?.cancel()
    }

    override fun close() {
        open = false
        channel.close()
        subscriptions.values.forEach { it.cancel() }
        subscriptions.clear()
    }

    override suspend fun complete() {
        // Nothing to do.
    }

    private fun verifyState() {
        log.trace { "Verifying topic state" }
        if (!open) {
            log.trace { "Topic is closed" }
            throw ClosedTopicException()
        }
        log.trace { "Topic is open" }
    }

    companion object {

        @JvmStatic
        private val log = KotlinLogging.logger { }
    }
}
