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

package io.qalipsis.api.messaging

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
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
        if (maximalTopicSize in 0 until currentTopicSize) {
            log.trace { "Compacting the size of the topic from $currentTopicSize to $maximalTopicSize" }
            while (currentTopicSize > maximalTopicSize) {
                subscriptionSlot = subscriptionSlot.get().next
                currentTopicSize--
            }
            log.trace { "The size of the topic was compacted to $currentTopicSize" }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
