/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.events

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventsPublisher
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Event publisher using Slf4j.
 *
 * The events are saved according to their name prefixed by 'events'.
 *
 * This uses the root logger "events" and is enabled when the property 'events.export.slf4j.enabled' is set to true.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(property = "events.export.slf4j.enabled", defaultValue = "false", value = "true"),
    Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
)
internal class Slf4JEventsPublisher : EventsPublisher {

    private val loggers = ConcurrentHashMap<String, Logger>()

    override fun publish(event: Event) {
        val logger = loggers.computeIfAbsent("$LOGGER_PREFIX.${event.name}", LoggerFactory::getLogger)

        when (event.level) {
            EventLevel.ERROR -> logger.error(toMessage(event))
            EventLevel.WARN -> logger.warn(toMessage(event))
            EventLevel.INFO -> logger.info(toMessage(event))
            EventLevel.DEBUG -> logger.debug(toMessage(event))
            EventLevel.TRACE -> logger.trace(toMessage(event))
            else -> Unit
        }
    }

    private fun toMessage(event: Event): String {
        return event.run { "${name};${value ?: ""};${tags.joinToString(",") { "${it.key}=${it.value}" }}" }
    }

    companion object {

        private const val LOGGER_PREFIX = "events."

    }

}
