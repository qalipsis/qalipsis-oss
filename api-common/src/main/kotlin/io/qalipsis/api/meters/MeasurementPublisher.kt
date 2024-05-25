/*
 * Copyright 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.api.meters

/**
 * Handles publishing of meter snapshots to different databases and monitoring systems.
 *
 * @author Francisca Eze
 */
interface MeasurementPublisher {

    /**
     * Handles setup and initialization of registered measurement publishers.
     */
    suspend fun init() = Unit

    /**
     * Saves/publishes received meters to their corresponding publishing strategies.
     */
    suspend fun publish(meters: Collection<MeterSnapshot<*>>)

    /**
     * Shuts down all running publishers.
     */
    suspend fun stop() = Unit
}