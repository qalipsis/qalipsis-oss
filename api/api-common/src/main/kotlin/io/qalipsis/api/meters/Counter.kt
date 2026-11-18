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

package io.qalipsis.api.meters

/**
 * Custom interface with methods adopted from the java micrometer-core library.
 * Used to track monotonically increasing values.
 *
 * @author Francisca Eze
 */
interface Counter : Meter<Counter> {
    /**
     * Update the counter by one.
     */
    fun increment()

    /**
     * Update the counter by `amount`.
     * @param amount amount to add to the counter.
     */
    fun increment(amount: Double)

    /**
     * Returns the cumulative count since this counter was created.
     */
    fun count(): Double
}