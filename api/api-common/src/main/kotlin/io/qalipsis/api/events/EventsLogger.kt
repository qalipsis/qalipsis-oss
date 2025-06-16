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

import java.time.Instant
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

/**
 * Logger for events in order to track what exactly happens in Qalipsis.
 *
 * The [EventsLogger] is coupled with [EventsPublisher]s, that are in charge of publishing the events to remote systems
 * or log files.
 *
 * @author Eric Jessé
 */
interface EventsLogger {

    /**
     * Adds [tags] to all the generated events.
     */
    fun configureTags(tags: Map<String, String>)

    /**
     * Log an event with the provided level.
     *
     * @param level the priority of the event.
     * @param name the name of the event. They are dot-separated strings of kebab-cased (dash-separated lowercase words) token
     * and in the form of "object-state", e.g: step-started, minion.execution-complete.
     * @param value the potential value. Any type can be used, its interpretation is let to the implementation.
     * @param timestamp the instant of the event, defaults to now.
     * @param tagsSupplier statement to generate the map of tags, if the event ever has to be logged.
     */
    fun log(
        level: EventLevel, name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tagsSupplier: () -> Map<String, String>
    )

    /**
     * Log an event with the provided level. If creating the map of tags has a cost, prefer to the use
     * the equivalent method with a tags supplier expression.
     *
     * @param level the priority of the event.
     * @param name the name of the event. They are dot-separated strings of kebab-cased (dash-separated lowercase words) token
     * and in the form of "object-state", e.g: step-started, minion.execution-complete.
     * @param value the potential value. Any type can be used, its interpretation is let to the implementation.
     * @param timestamp the instant of the event, defaults to now.
     * @param tags a map of tags.
     */
    fun log(
        level: EventLevel, name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tags: Map<String, String> = emptyMap()
    )

    fun log(
        level: EventLevel, name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        vararg tags: Pair<String, String>
    ) {
        log(level, name, value, timestamp) { tags.toMap() }
    }

    fun trace(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tags: Map<String, String> = emptyMap()
    ) {
        log(EventLevel.TRACE, name, value, timestamp, tags)
    }

    fun trace(name: String, value: Any? = null, timestamp: Instant = Instant.now(), vararg tags: Pair<String, String>) {
        log(EventLevel.TRACE, name, value, timestamp, *tags)
    }

    fun trace(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tagsSupplier: (() -> Map<String, String>)
    ) {
        log(EventLevel.TRACE, name, value, timestamp, tagsSupplier)
    }

    fun debug(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tags: Map<String, String> = emptyMap()
    ) {
        log(EventLevel.DEBUG, name, value, timestamp, tags)
    }

    fun debug(name: String, value: Any? = null, timestamp: Instant = Instant.now(), vararg tags: Pair<String, String>) {
        log(EventLevel.DEBUG, name, value, timestamp, *tags)
    }

    fun debug(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tagsSupplier: (() -> Map<String, String>)
    ) {
        log(EventLevel.DEBUG, name, value, timestamp, tagsSupplier)
    }

    fun info(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tags: Map<String, String> = emptyMap()
    ) {
        log(EventLevel.INFO, name, value, timestamp, tags)
    }

    fun info(name: String, value: Any? = null, timestamp: Instant = Instant.now(), vararg tags: Pair<String, String>) {
        log(EventLevel.INFO, name, value, timestamp, *tags)
    }

    fun info(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tagsSupplier: (() -> Map<String, String>)
    ) {
        log(EventLevel.INFO, name, value, timestamp, tagsSupplier)
    }

    fun warn(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tags: Map<String, String> = emptyMap()
    ) {
        log(EventLevel.WARN, name, value, timestamp, tags)
    }

    fun warn(name: String, value: Any? = null, timestamp: Instant = Instant.now(), vararg tags: Pair<String, String>) {
        log(EventLevel.WARN, name, value, timestamp, *tags)
    }

    fun warn(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tagsSupplier: (() -> Map<String, String>)
    ) {
        log(EventLevel.WARN, name, value, timestamp, tagsSupplier)
    }

    fun error(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tags: Map<String, String> = emptyMap()
    ) {
        log(EventLevel.ERROR, name, value, timestamp, tags)
    }

    fun error(name: String, value: Any? = null, timestamp: Instant = Instant.now(), vararg tags: Pair<String, String>) {
        log(EventLevel.ERROR, name, value, timestamp, *tags)
    }

    fun error(
        name: String, value: Any? = null, timestamp: Instant = Instant.now(),
        tagsSupplier: (() -> Map<String, String>)
    ) {
        log(EventLevel.ERROR, name, value, timestamp, tagsSupplier)
    }

    /**
     * Starts the logger.
     */
    @PostConstruct
    fun start() = Unit

    /**
     * Closes the logger.
     */
    @PreDestroy
    fun stop() = Unit
}
