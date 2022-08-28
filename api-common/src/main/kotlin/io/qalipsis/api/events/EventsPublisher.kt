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

package io.qalipsis.api.events

/**
 * Interface for instances in charge of publishing the [Event]s to external systems.
 *
 * When enabled, the publishers are injected into an [EventsLogger] in order to receive the data.
 * The [EventsPublisher]s should never be used directly from an instance generating an event.
 *
 * @author Eric Jessé
 */
interface EventsPublisher {

    /**
     * Receives an [Event] so that it can be published. The implementation decides what to exactly do:
     * add to a buffer, push to an external system...
     */
    fun publish(event: Event)

    /**
     * Initializes and start the logger.
     */
    fun start() = Unit

    /**
     * Performs all the closing operations.
     */
    fun stop() = Unit
}
