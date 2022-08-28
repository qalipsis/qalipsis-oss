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
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/**
 * [LoopTopic] is a special implementation of [Topic] providing the data as an infinite loop: once the consumer polled
 * all the values from the topic, values are provided from the beginning.
 *
 * @param idleTimeout idle time of a subscription: once a subscription passed this duration without record, it is cancelled.
 *
 * @author Eric Jessé
 */
internal open class LoopTopic<T>(
    idleTimeout: Duration
) : AbstractLinkedSlotsBasedTopic<T>(idleTimeout) {

    private var shouldComplete = false

    private var complete = false

    override suspend fun updateSubscriptionSlot(lastSetSlot: ImmutableSlot<LinkedRecord<T>>) {
        // If the topic was tried to be completed but failed because it was empty, it is done now.
        if (shouldComplete != complete) {
            complete()
        }
    }

    override suspend fun complete() {
        if (!complete) {
            shouldComplete = true
            writeMutex.withLock {
                // If there is at least on item, the chain is closed to form the loop.
                if (writeSlot !== subscriptionSlot) {
                    writeSlot.set(subscriptionSlot.get())
                    complete = true
                }
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}