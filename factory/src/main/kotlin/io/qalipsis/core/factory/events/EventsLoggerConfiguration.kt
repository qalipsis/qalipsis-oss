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

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.naming.conventions.StringConvention
import io.qalipsis.api.events.EventLevel
import io.qalipsis.core.configuration.ExecutionEnvironments
import javax.annotation.PostConstruct


/**
 * Configuration of the events logging.
 *
 * @property root level of the events, when not explicitly defined in [level]
 * @property level explicit of level to filter the events
 *
 * @author Eric Jessé
 */
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
@ConfigurationProperties("events")
class EventsLoggerConfiguration(
    val environment: Environment
) {
    var root: EventLevel = EventLevel.TRACE

    lateinit var level: Map<String, EventLevel>

    @PostConstruct
    fun init() {
        val properties: MutableMap<String, Any> = HashMap(environment.getProperties("events.level"))
        // Using raw keys here allows configuring log levels for camelCase package names in application.yml
        properties.putAll(environment.getProperties("events.level", StringConvention.RAW))
        val levels = mutableMapOf<String, EventLevel>()
        properties.forEach { (loggerPrefix, levelValue) ->
            levels[loggerPrefix] = EventLevel.valueOf("$levelValue".uppercase())
        }
        level = levels
    }

}
