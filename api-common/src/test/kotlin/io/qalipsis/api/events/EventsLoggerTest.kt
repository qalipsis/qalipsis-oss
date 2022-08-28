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
