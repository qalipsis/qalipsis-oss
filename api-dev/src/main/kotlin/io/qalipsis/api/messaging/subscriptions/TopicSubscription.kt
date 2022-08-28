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

package io.qalipsis.api.messaging.subscriptions

import io.qalipsis.api.messaging.Record
import kotlinx.coroutines.Job

/**
 * Subscription to consume records from a topic.
 *
 * @author Eric Jessé
 */
interface TopicSubscription<T> {

    /**
     * Polls the next record from the topic.
     */
    suspend fun poll(): Record<T>

    /**
     * Polls the next record from the topic and directly returns its value.
     */
    suspend fun pollValue(): T {
        return poll().value
    }

    /**
     * Executes an action on each received value.
     *
     * @return the [Job] running the consumption and execution of the handler.
     */
    suspend fun onReceiveValue(block: suspend (T) -> Unit): Job

    /**
     * Returns `true` if the subscription is still active, otherwise `false`.
     */
    fun isActive(): Boolean

    /**
     * Cancels the subscription.
     */
    fun cancel()

}