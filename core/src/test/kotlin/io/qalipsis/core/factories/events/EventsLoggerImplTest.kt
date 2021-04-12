package io.qalipsis.core.factories.events

import io.mockk.confirmVerified
import io.mockk.spyk
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventsPublisher
import io.qalipsis.api.events.toTags
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyExactly
import io.qalipsis.test.mockk.verifyNever
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Properties


/**
 * @author Eric Jessé
 */
internal class EventsLoggerImplTest {

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
            logger.checkLevelAndLogWithSupplier(any(), any(), any(), any(), any())
        }
    }

    @Test
    internal fun `should not log anything when the root is OFF and there is no specific level but there are publishers`() {
        // given
        val logger = spyk(EventsLoggerImpl(configuration(EventLevel.OFF), listOf(relaxedMockk())),
            recordPrivateCalls = true)

        // when
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        // then
        verifyNever {
            logger.checkLevelAndLog(any(), any(), any(), any(), any())
            logger.checkLevelAndLogWithSupplier(any(), any(), any(), any(), any())
        }
    }

    @Test
    internal fun `should not log anything when the root is OFF and there is only specific level to OFF and there are publishers`() {
        // given
        val levels = Properties().also {
            it["event"] = "OFF"
            it["event.topic"] = "off"
        }
        val logger = spyk(EventsLoggerImpl(configuration(EventLevel.OFF, levels), listOf(relaxedMockk())),
            recordPrivateCalls = true)

        // when
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, EVENT_TAGS_MAP_SUPPLIER)
        logger.log(EventLevel.ERROR, EVENT_NAME, EVENT_VALUE, EVENT_INSTANT, *EVENT_TAGS_PAIRS)

        // then
        verifyNever {
            logger.checkLevelAndLog(any(), any(), any(), any(), any())
            logger.checkLevelAndLogWithSupplier(any(), any(), any(), any(), any())
        }
    }

    @Test
    internal fun `should log only when the root level allows it`() {
        // given
        val publisher1 = relaxedMockk<EventsPublisher>()
        val publisher2 = relaxedMockk<EventsPublisher>()
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
        val levels = Properties().also {
            it[EVENT_NAME] = "info"
            it[EVENT_NAME_PARENT] = "off"
        }

        val publisher1 = relaxedMockk<EventsPublisher>()
        val publisher2 = relaxedMockk<EventsPublisher>()
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
        val levels = Properties().also {
            it[EVENT_NAME_PARENT] = "info"
        }

        val publisher1 = relaxedMockk<EventsPublisher>()
        val publisher2 = relaxedMockk<EventsPublisher>()
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
        val publisher1 = relaxedMockk<EventsPublisher>()
        val publisher2 = relaxedMockk<EventsPublisher>()
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
        val publisher1 = relaxedMockk<EventsPublisher>()
        val publisher2 = relaxedMockk<EventsPublisher>()
        val logger = EventsLoggerImpl(configuration(EventLevel.OFF), listOf(publisher1, publisher2))

        // when
        logger.start()

        // then
        confirmVerified(publisher1, publisher2)
    }

    @Test
    internal fun `should stop the publishers when events are to be logged`() {
        // given
        val publisher1 = relaxedMockk<EventsPublisher>()
        val publisher2 = relaxedMockk<EventsPublisher>()
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
        val publisher1 = relaxedMockk<EventsPublisher>()
        val publisher2 = relaxedMockk<EventsPublisher>()
        val logger = EventsLoggerImpl(configuration(EventLevel.OFF), listOf(publisher1, publisher2))

        // when
        logger.stop()

        // then
        confirmVerified(publisher1, publisher2)
    }

    /**
     * Creates a configuration for the events logger.
     */
    private fun configuration(root: EventLevel = EventLevel.TRACE, level: Properties = Properties()) =
        EventsLoggerConfiguration().apply {
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
