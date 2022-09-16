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

import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventTag
import io.qalipsis.api.events.EventsPublisher
import io.qalipsis.api.events.toTags
import io.qalipsis.core.factory.events.catadioptre.checkLevelAndLog
import io.qalipsis.core.factory.events.catadioptre.checkLevelAndLogWithSupplier
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyExactly
import io.qalipsis.test.mockk.verifyNever
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * @author Eric Jessé
 */
@WithMockk
internal class EventsLoggerImplTest {

    @RelaxedMockK
    lateinit var publisher1: EventsPublisher

    @RelaxedMockK
    lateinit var publisher2: EventsPublisher

    @Test
    internal fun `should not log anything when there are no publishers`() {
        // given
        val logger =
            spyk(EventsLoggerImpl(configuration(EventLevel.TRACE), emptyList()), recordPrivateCalls = true)

        // when
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        // then
        verifyNever {
            logger.checkLevelAndLog(any(), any(), any(), any(), any())
            logger["checkLevelAndLogWithSupplier"](
                any<EventLevel>(),
                any<String>(),
                any(),
                any<Instant>(),
                any<() -> Map<String, String>>()
            )
        }
    }

    @Test
    internal fun `should not log anything when the root is OFF and there is no specific level but there are publishers`() {
        // given
        val logger = spyk(
            EventsLoggerImpl(configuration(EventLevel.OFF), listOf(relaxedMockk())),
            recordPrivateCalls = true
        )

        // when
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        // then
        verifyNever {
            logger.checkLevelAndLog(any(), any(), any(), any(), any())
            logger["checkLevelAndLogWithSupplier"](
                any<EventLevel>(),
                any<String>(),
                any(),
                any<Instant>(),
                any<() -> Map<String, String>>()
            )
        }
    }

    @Test
    internal fun `should not log anything when the root is OFF and there is only specific level to OFF and there are publishers`() {
        // given
        val levels = mapOf("event" to EventLevel.OFF, "event.topic" to EventLevel.OFF)
        val logger = spyk(
            EventsLoggerImpl(configuration(EventLevel.OFF, levels), listOf(relaxedMockk())),
            recordPrivateCalls = true
        )

        // when
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        // then
        verifyNever {
            logger.checkLevelAndLog(any(), any(), any(), any(), any())
            logger["checkLevelAndLogWithSupplier"](
                any<EventLevel>(),
                any<String>(),
                any(),
                any<Instant>(),
                any<() -> Map<String, String>>()
            )
        }
    }

    @Test
    internal fun `should log only when the root level allows it`() {
        // given
        val logger = EventsLoggerImpl(configuration(EventLevel.INFO), listOf(publisher1, publisher2))

        // when
        logger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)

        // then
        verifyExactly(2) { // Everything is called twice, once with the map of tags, once with the supplier.
            publisher1.publish(EventLevel.INFO.expectedEvent)
            publisher2.publish(EventLevel.INFO.expectedEvent)
            publisher1.publish(EventLevel.WARN.expectedEvent)
            publisher2.publish(EventLevel.WARN.expectedEvent)
            publisher1.publish(EventLevel.ERROR.expectedEvent)
            publisher2.publish(EventLevel.ERROR.expectedEvent)
        }

        confirmVerified(publisher1, publisher2)
    }

    @Test
    internal fun `should log when the exact level allows it`() {
        // given
        val levels = mapOf(EVENT_NAME to EventLevel.INFO, EVENT_NAME_PARENT to EventLevel.OFF)

        val logger = EventsLoggerImpl(configuration(EventLevel.OFF, levels), listOf(publisher1, publisher2))

        // when
        logger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)

        // then
        verifyExactly(2) { // Everything is called twice, once with the map of tags, once with the supplier.
            publisher1.publish(EventLevel.INFO.expectedEvent)
            publisher2.publish(EventLevel.INFO.expectedEvent)
            publisher1.publish(EventLevel.WARN.expectedEvent)
            publisher2.publish(EventLevel.WARN.expectedEvent)
            publisher1.publish(EventLevel.ERROR.expectedEvent)
            publisher2.publish(EventLevel.ERROR.expectedEvent)
        }

        confirmVerified(publisher1, publisher2)
    }

    @Test
    internal fun `should log when a parent level allows it`() {
        // given
        val levels = mapOf(EVENT_NAME_PARENT to EventLevel.INFO)

        val logger = EventsLoggerImpl(configuration(EventLevel.OFF, levels), listOf(publisher1, publisher2))

        // when
        logger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)

        // then
        verifyExactly(2) { // Everything is called twice, once with the map of tags, once with the supplier.
            publisher1.publish(EventLevel.INFO.expectedEvent)
            publisher2.publish(EventLevel.INFO.expectedEvent)
            publisher1.publish(EventLevel.WARN.expectedEvent)
            publisher2.publish(EventLevel.WARN.expectedEvent)
            publisher1.publish(EventLevel.ERROR.expectedEvent)
            publisher2.publish(EventLevel.ERROR.expectedEvent)
        }

        confirmVerified(publisher1, publisher2)
    }

    @Test
    internal fun `should start the publishers when events are to be logged`() {
        // given
        val logger = EventsLoggerImpl(configuration(), listOf(publisher1, publisher2))

        // when
        logger.start()

        // then
        verifyOnce {
            publisher1.start()
            publisher2.start()
        }
    }

    @Test
    internal fun `should not start the publishers when no event is to be logged`() {
        // given
        val logger = EventsLoggerImpl(configuration(EventLevel.OFF), listOf(publisher1, publisher2))

        // when
        logger.start()

        // then
        confirmVerified(publisher1, publisher2)
    }

    @Test
    internal fun `should stop the publishers when events are to be logged`() {
        // given
        val logger = EventsLoggerImpl(configuration(), listOf(publisher1, publisher2))

        // when
        logger.stop()

        // then
        verifyOnce {
            publisher1.stop()
            publisher2.stop()
        }
    }

    @Test
    internal fun `should not stop the publishers when no event is to be logged`() {
        // given
        val logger = EventsLoggerImpl(configuration(EventLevel.OFF), listOf(publisher1, publisher2))

        // when
        logger.stop()

        // then
        confirmVerified(publisher1, publisher2)
    }

    @Test
    internal fun `should log with configured and event tags`() {
        // given
        val logger = EventsLoggerImpl(configuration(EventLevel.TRACE), listOf(publisher1))
        logger.configureTags(mapOf("key1" to "value1", "key2" to "value2"))
        val timestamp = Instant.now()

        // when
        logger.checkLevelAndLog(
            EventLevel.DEBUG,
            "my-event",
            "the-value",
            timestamp,
            mapOf("key2" to "valueOtherThan2", "key3" to "value3")
        )

        // then
        verifyOnce {
            publisher1.publish(
                Event(
                    "my-event", EventLevel.DEBUG, listOf(
                        EventTag("key1", "value1"),
                        EventTag("key2", "value2"),
                        EventTag("key2", "valueOtherThan2"),
                        EventTag("key3", "value3")
                    ), "the-value", timestamp
                )
            )
        }
    }

    @Test
    internal fun `should log with configured and event tags from supplier`() {
        // given
        val logger = EventsLoggerImpl(configuration(EventLevel.TRACE), listOf(publisher1))
        logger.configureTags(mapOf("key1" to "value1", "key2" to "value2"))
        val timestamp = Instant.now()

        // when
        logger.checkLevelAndLogWithSupplier(EventLevel.DEBUG, "my-event", "the-value", timestamp) {
            mapOf("key2" to "valueOtherThan2", "key3" to "value3")
        }

        // then
        verifyOnce {
            publisher1.publish(
                Event(
                    "my-event", EventLevel.DEBUG, listOf(
                        EventTag("key1", "value1"),
                        EventTag("key2", "value2"),
                        EventTag("key2", "valueOtherThan2"),
                        EventTag("key3", "value3")
                    ), "the-value", timestamp
                )
            )
        }
    }

    /**
     * Creates a configuration for the events logger.
     */
    private fun configuration(root: EventLevel = EventLevel.TRACE, level: Map<String, EventLevel> = emptyMap()) =
        EventsLoggerConfiguration(relaxedMockk()).apply {
            this.root = root
            this.level = level
        }

    /**
     * Builds an event with the expected data from the provided level.
     */
    private val EventLevel.expectedEvent
        get() = Event(EVENT_NAME, this, EVENT_TAGS, EVENT_VALUE, EVENT_INSTANT)

    companion object {

        private const val EVENT_NAME_PARENT = "the.event"

        private const val EVENT_NAME = "${EVENT_NAME_PARENT}.name"

        private const val EVENT_VALUE = "the value"

        private val EVENT_INSTANT = Instant.now()

        private val EVENT_TAGS_MAP = mapOf("key-1" to "value-1", "key-2" to "value-2")

        private val EVENT_TAGS_MAP_SUPPLIER = { mapOf("key-1" to "value-1", "key-2" to "value-2") }

        private val EVENT_TAGS_PAIRS = arrayOf("key-1" to "value-1", "key-2" to "value-2")

        private val EVENT_TAGS = EVENT_TAGS_MAP.toTags()

    }
}
