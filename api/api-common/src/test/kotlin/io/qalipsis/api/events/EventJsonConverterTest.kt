package io.qalipsis.api.events

import io.mockk.every
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.io.PrintWriter
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal class EventJsonConverterTest{
    
    private val converter = EventJsonConverter()
    
    @Test
    fun `should write document with tags`() {
        val event =
            Event(
                "my-event", EventLevel.INFO, listOf(EventTag("key-1", "value-1"), EventTag("key-2", "value-2")),
                null
            )
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","tags":{"key-1":"value-1","key-2":"value-2"}}""",
            result, false
        )
    }

    @Test
    fun `should write document without tags`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), null)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info"}""",
            result,
            false
        )
    }

    @Test
    fun `should write document with value as string`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), "my-message")
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","message":"my-message"}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as boolean`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), true)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","boolean":true}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as finite double`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), 123.65)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","number":123.65}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as positive infinite double`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), Double.POSITIVE_INFINITY)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","non-finite-decimal":"Infinity"}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as negative infinite double`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), Double.NEGATIVE_INFINITY)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","non-finite-decimal":"-Infinity"}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as NaN double`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), Double.NaN)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","non-finite-decimal":"NaN"}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as float`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), 123.65.toFloat())
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","number":123.65}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as big decimal`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), 123.65.toBigDecimal())
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","number":123.65}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as integer`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), 123)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","number":123}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as big integer`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), 123.toBigInteger())
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","number":123}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as long`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), 123.toLong())
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","number":123}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as instant`() {
        val now = Instant.now()
        val event = Event("my-event", EventLevel.INFO, emptyList(), now)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","date":"${
                DateTimeFormatter.ISO_INSTANT.format(now)
            }"}""", result, false
        )
    }

    @Test
    fun `should write document with value as zoned date time`() {
        val now = ZonedDateTime.now()
        val event = Event("my-event", EventLevel.INFO, emptyList(), now)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","date":"${
                DateTimeFormatter.ISO_INSTANT.format(now)
            }"}""", result, false
        )
    }

    @Test
    fun `should write document with value as local date time`() {
        val now = LocalDateTime.now()
        val event = Event("my-event", EventLevel.INFO, emptyList(), now)
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","date":"${
                DateTimeFormatter.ISO_INSTANT.format(now.atZone(ZoneId.systemDefault()))
            }"}""", result, false
        )
    }

    @Test
    fun `should write document with value as throwable`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), relaxedMockk<Throwable> {
            every { message } returns "my-error"
            every { printStackTrace(any<PrintWriter>()) } answers {
                (firstArg() as PrintWriter).write("this is the stack")
            }
        })
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","error":"my-error","stack-trace":"this is the stack"}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as duration as decimal seconds`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), Duration.ofNanos(12_123_456_789))
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","duration":12.123456789}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as geo point`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), EventGeoPoint(12.34, 34.76))
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","point":[34.76,12.34]}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as range excluding upper bound`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), EventRange(12.34, 34.76, includeUpper = false))
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","numeric-range":{"gte":12.34,"lt":34.76}}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as range excluding lower bound`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), EventRange(12.34, 34.76, includeLower = false))
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","numeric-range":{"gt":12.34,"lte":34.76}}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as array`() {
        val event = Event(
            "my-event",
            EventLevel.INFO,
            emptyList(),
            listOf(12.34, 8765, "here is the test", "here is the other test", Duration.ofMillis(123))
        )
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","number":12.34,"message":"here is the test","duration":0.123,"values":["8765","here is the other test"]}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as iterable`() {
        val event = Event(
            "my-event",
            EventLevel.INFO,
            emptyList(),
            listOf(12.34, 8765, "here is the test", "here is the other test", Duration.ofMillis(123))
        )
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","number":12.34,"message":"here is the test","duration":0.123,"values":["8765","here is the other test"]}""",
            result, false
        )
    }

    @Test
    fun `should write document with value as any object`() {
        val event = Event("my-event", EventLevel.INFO, emptyList(), MyTestObject())
        val result = converter.convert(event)

        JSONAssert.assertEquals(
            """{"@timestamp":${expectedTimestamp(event)},"name":"my-event","level":"info","value":"{\"property1\":1243.65,\"property2\":\"here is the test\"}"}""",
            result, false
        )
    }

    private fun expectedTimestamp(event: Event) = event.timestamp.toEpochMilli()

    data class MyTestObject(val property1: Double = 1243.65, val property2: String = "here is the test")
}
