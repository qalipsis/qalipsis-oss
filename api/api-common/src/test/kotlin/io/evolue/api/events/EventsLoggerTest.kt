package io.evolue.api.events

import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.verifyOnce
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Tests to validate the default implementations of [EventsLogger].
 * @author Eric Jessé
 */
@WithMockk
class EventsLoggerTest {

    @RelaxedMockK
    lateinit var mockedLogger: EventsLogger

    @InjectMockKs
    lateinit var logger: TestEventsLogger

    @Test
    internal fun `should log as trace with map of tags`() {
        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP) }
    }

    @Test
    internal fun `should log as trace with supplier of map of tags`() {
        logger.trace(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce { mockedLogger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER) }
    }

    @Test
    internal fun `should log as trace with var args tags`() {
        var tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, tagsSupplier = capture(tagsSupplierSlot))
        } returns Unit
        logger.trace(EVENT_NAME, EVENT_VALUE, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.TRACE, EVENT_NAME, EVENT_VALUE, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    @Test
    internal fun `should log as debug with map of tags`() {
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP) }
    }

    @Test
    internal fun `should log as debug with supplier of map of tags`() {
        logger.debug(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce { mockedLogger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER) }
    }

    @Test
    internal fun `should log as debug with var args tags`() {
        var tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, tagsSupplier = capture(tagsSupplierSlot))
        } returns Unit
        logger.debug(EVENT_NAME, EVENT_VALUE, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.DEBUG, EVENT_NAME, EVENT_VALUE, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    @Test
    internal fun `should log as info with map of tags`() {
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP) }
    }

    @Test
    internal fun `should log as info with supplier of map of tags`() {
        logger.info(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce { mockedLogger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER) }
    }

    @Test
    internal fun `should log as info with var args tags`() {
        var tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, tagsSupplier = capture(tagsSupplierSlot))
        } returns Unit
        logger.info(EVENT_NAME, EVENT_VALUE, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.INFO, EVENT_NAME, EVENT_VALUE, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    @Test
    internal fun `should log as warn with map of tags`() {
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP) }
    }

    @Test
    internal fun `should log as warn with supplier of map of tags`() {
        logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce { mockedLogger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER) }
    }

    @Test
    internal fun `should log as warn with var args tags`() {
        var tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, tagsSupplier = capture(tagsSupplierSlot))
        } returns Unit
        logger.warn(EVENT_NAME, EVENT_VALUE, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.WARN, EVENT_NAME, EVENT_VALUE, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    @Test
    internal fun `should log as error with map of tags`() {
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)

        verifyOnce { mockedLogger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP) }
    }

    @Test
    internal fun `should log as error with supplier of map of tags`() {
        logger.error(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER)

        verifyOnce { mockedLogger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP_SUPPLIER) }
    }

    @Test
    internal fun `should log as error with var args tags`() {
        var tagsSupplierSlot = slot<(() -> Map<String, String>)>()
        every {
            mockedLogger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, tagsSupplier = capture(tagsSupplierSlot))
        } returns Unit
        logger.error(EVENT_NAME, EVENT_VALUE, *EVENT_TAGS_PAIRS)

        verifyOnce { mockedLogger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, tagsSupplier = any()) }
        Assertions.assertEquals(EVENT_TAGS_MAP, tagsSupplierSlot.captured())
    }

    class TestEventsLogger(private val mockedLogger: EventsLogger) : EventsLogger {
        override fun log(level: EventLevel, name: String, value: Any?, tagsSupplier: () -> Map<String, String>) {
            mockedLogger.log(level, name, value, tagsSupplier)
        }

        override fun log(level: EventLevel, name: String, value: Any?, tags: Map<String, String>) {
            mockedLogger.log(level, name, value, tags)
        }
    }

    companion object {

        const val EVENT_NAME = "event-name"

        const val EVENT_VALUE = "my-value"

        val EVENT_TAGS_MAP = mapOf("key-1" to "value-1", "key-2" to "value-2")

        val EVENT_TAGS_MAP_SUPPLIER = { mapOf("key-1" to "value-1", "key-2" to "value-2") }

        val EVENT_TAGS_PAIRS = arrayOf("key-1" to "value-1", "key-2" to "value-2")
    }
}
