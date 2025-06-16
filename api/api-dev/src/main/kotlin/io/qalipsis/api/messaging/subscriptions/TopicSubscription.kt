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