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

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.key
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.events.EventsPublisher
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout


internal class EventsAndPublisherIntegrationTest {

    @Nested
    @MicronautTest(environments = ["nopublisher", ExecutionEnvironments.FACTORY], startApplication = false)
    inner class NoPublisher {

        @Inject
        private lateinit var applicationContext : ApplicationContext

        /**
         * Uses the resource application-nopublisher.yml to load the configuration.
         */
        @Test
        @Timeout(10)
        internal fun `should start without publisher`() {
            assertThat(applicationContext.getBeansOfType(EventsPublisher::class.java)).isEmpty()
            assertThat(applicationContext.getBean(EventsLogger::class.java)).all {
                typedProp<Collection<EventsPublisher>>("publishers").isEmpty()
                prop("rootLevel").isEqualTo(EventLevel.INFO)
                prop("enabled").isEqualTo(false)
                typedProp<List<Pair<String, EventLevel>>>("declaredLevels").transform { it.toMap() }.all {
                    hasSize(3)
                    key("the.first").isEqualTo(EventLevel.INFO)
                    key("the.second.level").isEqualTo(EventLevel.DEBUG)
                    key("the.third.level").isEqualTo(EventLevel.OFF)
                }
            }
        }
    }

    @Nested
    @MicronautTest(environments = ["slf4j-publisher", ExecutionEnvironments.FACTORY], startApplication = false)
    inner class Slf4jPublisher {

        @Inject
        private lateinit var applicationContext : ApplicationContext

        /**
         * Uses the resource application-slf4j-publisher.yml to load the configuration.
         */
        @Test
        @Timeout(10)
        internal fun `should start with the slf4j publisher`() {
            assertThat(applicationContext.getBeansOfType(EventsPublisher::class.java)).all {
                hasSize(1)
                any { it.isInstanceOf(Slf4JEventsPublisher::class) }
            }
            assertThat(applicationContext.getBean(EventsLogger::class.java)).all {
                typedProp<Collection<EventsPublisher>>("publishers").all {
                    hasSize(1)
                    any { it.isInstanceOf(Slf4JEventsPublisher::class) }
                }
                prop("rootLevel").isEqualTo(EventLevel.ERROR)
                prop("enabled").isEqualTo(true)
                typedProp<List<Pair<String, EventLevel>>>("declaredLevels").transform { it.toMap() }.all {
                    hasSize(4)
                    key("the.first").isEqualTo(EventLevel.WARN)
                    key("the.second.level").isEqualTo(EventLevel.INFO)
                    key("the.second.other.level").isEqualTo(EventLevel.DEBUG)
                    key("the.third.level").isEqualTo(EventLevel.ERROR)
                }
            }
        }
    }
}
