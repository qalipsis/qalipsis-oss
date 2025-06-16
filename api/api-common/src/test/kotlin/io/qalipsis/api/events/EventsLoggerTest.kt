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

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Tests to validate the default implementations of [EventsLogger].
 * @author Eric Jessé
 */
@WithMockk
internal class EventsLoggerTest {

    @RelaxedMockK
    lateinit var mockedLogger: EventsLogger

    @InjectMockKs
    lateinit var logger: TestEventsLogger

    @Test
    fun `should log as trace with map of tags`() {
        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP) }
    }

    @Test
    fun `should log as trace with supplier of map of tags`() {
        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce {
            mockedLogger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        }
    }

    @Test
    fun `should log as trace with var args tags`() {
        val tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(
                EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT,
                tagsSupplier = capture(tagsSupplierSlot)
            )
        } returns Unit
        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    @Test
    fun `should log as debug with map of tags`() {
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP) }
    }

    @Test
    fun `should log as debug with supplier of map of tags`() {
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce {
            mockedLogger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        }
    }

    @Test
    fun `should log as debug with var args tags`() {
        val tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(
                EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT,
                tagsSupplier = capture(tagsSupplierSlot)
            )
        } returns Unit
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    @Test
    fun `should log as info with map of tags`() {
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP) }
    }

    @Test
    fun `should log as info with supplier of map of tags`() {
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce {
            mockedLogger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        }
    }

    @Test
    fun `should log as info with var args tags`() {
        val tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(
                EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT,
                tagsSupplier = capture(tagsSupplierSlot)
            )
        } returns Unit
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    @Test
    fun `should log as warn with map of tags`() {
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP) }
    }

    @Test
    fun `should log as warn with supplier of map of tags`() {
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce {
            mockedLogger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        }
    }

    @Test
    fun `should log as warn with var args tags`() {
        val tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(
                EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT,
                tagsSupplier = capture(tagsSupplierSlot)
            )
        } returns Unit
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    @Test
    fun `should log as error with map of tags`() {
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP) }
    }

    @Test
    fun `should log as error with supplier of map of tags`() {
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce {
            mockedLogger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        }
    }

    @Test
    fun `should log as error with var args tags`() {
        val tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(
                EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT,
                tagsSupplier = capture(tagsSupplierSlot)
            )
        } returns Unit
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    class TestEventsLogger(private val mockedLogger: EventsLogger) : EventsLogger {

        override fun configureTags(tags: Map<String, String>) = Unit

        override fun log(
            level: EventLevel, name: String, value: Any?, timestamp: Instant,
            tagsSupplier: () -> Map<String, String>
        ) {
            mockedLogger.log(level, name, value, timestamp, tagsSupplier)
        }

        override fun log(level: EventLevel, name: String, value: Any?, timestamp: Instant, tags: Map<String, String>) {
            mockedLogger.log(level, name, value, timestamp, tags)
        }

    }

    companion object {

        private const val EVENT_NAME = "event-name"

        private const val EVENT_VALUE = "my-value"

        private val EVENT_INSTANT = Instant.now()

        private val EVENT_TAGS_MAP = mapOf("key-1" to "value-1", "key-2" to "value-2")

        private val EVENT_TAGS_MAP_SUPPLIER = { mapOf("key-1" to "value-1", "key-2" to "value-2") }

        private val EVENT_TAGS_PAIRS = arrayOf("key-1" to "value-1", "key-2" to "value-2")
    }
}
