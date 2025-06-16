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
