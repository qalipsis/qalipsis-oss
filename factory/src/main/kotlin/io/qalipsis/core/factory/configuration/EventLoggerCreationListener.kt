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

package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.context.event.BeanDestroyedEvent
import io.micronaut.context.event.BeanDestroyedEventListener
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton

/**
 * Implementation of [BeanCreatedEventListener] in order to start and stop the [EventsLogger]s.
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
class EventLoggerCreationListener(
    @Property(name = "factory.tenant", defaultValue = "_qalipsis_ten_") private val tenant: String,
    @Property(name = "factory.tags", defaultValue = "") private val tags: MutableMap<String, String>,
    @Property(name = "factory.zone", defaultValue = "") private val zone: String?
) : BeanCreatedEventListener<EventsLogger>,
    BeanDestroyedEventListener<EventsLogger> {

    override fun onCreated(event: BeanCreatedEvent<EventsLogger>): EventsLogger {
        tags["tenant"] = tenant
        if (!zone.isNullOrEmpty()) {
            tags["zone"] = zone
        }
        event.bean.apply {
            configureTags(tags)
            start()
        }
        return event.bean
    }

    override fun onDestroyed(event: BeanDestroyedEvent<EventsLogger>) {
        event.bean.stop()
    }

}