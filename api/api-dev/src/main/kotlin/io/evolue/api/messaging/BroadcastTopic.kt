package io.evolue.api.messaging

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.sync.ImmutableSlot
import java.time.Duration

/**
 * This class provides an implementation of [Topic] that forwards all the messages to all the subscriber.
 * Contrary to [BroadcastTopic], the new subscribers first receive all the records buffered so far.
 *
 * The implementation has a cost to consider, since it requires more processing to maintain thread-safety and buffering.
 *
 * @param idleTimeout idle time of a subscription: once a subscription passed this duration without record, it is cancelled.
 * @param maximalTopicSize maximal size of the topic: earliest records are discarded first.
 *
 * @author Eric Jessé
 */
internal open class BroadcastTopic<T>(
        private val maximalTopicSize: Int = -1,
        idleTimeout: Duration
) : AbstractLinkedSlotsBasedTopic<T>(idleTimeout) {

    private var currentTopicSize = 0

    override suspend fun updateSubscriptionSlot(lastSetSlot: ImmutableSlot<LinkedRecord<T>>) {
        currentTopicSize++
        if (maximalTopicSize >= 0) {
            while (currentTopicSize > maximalTopicSize) {
                log.trace("Reducing the size of the topic from $currentTopicSize to $maximalTopicSize")
                subscriptionSlot = subscriptionSlot.get().next
                currentTopicSize--
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}