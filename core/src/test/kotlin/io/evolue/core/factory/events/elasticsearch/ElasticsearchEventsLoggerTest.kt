package io.evolue.core.factory.eventslogger.elasticsearch

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.google.common.collect.Sets
import io.evolue.api.events.EventGeoPoint
import io.evolue.api.events.EventLevel
import io.evolue.api.events.EventRange
import io.evolue.core.factory.events.Event
import io.evolue.core.factory.events.EventTag
import io.evolue.core.factory.events.elasticsearch.ElasticsearchEventLoggerConfiguration
import io.evolue.core.factory.events.elasticsearch.ElasticsearchEventsLogger
import io.evolue.test.coroutines.CleanCoroutines
import io.evolue.test.mockk.relaxedMockk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.http.hostWithPort
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.skyscreamer.jsonassert.JSONAssert
import java.io.PrintWriter
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger


/**
 * Inspired by: https://github.com/micrometer-metrics/micrometer/blob/417ef01b7a46bd85b649ed5f9409ca21fcbca438/implementations/micrometer-registry-elastic/src/test/java/io/micrometer/elastic/ElasticMeterRegistryTest.java
 *
 * @author Eric Jessé
 */
@CleanCoroutines
internal class ElasticsearchEventsLoggerTest {

    // The meter registry should provide a timer that execute the expressions to record.
    private val meterRegistry: MeterRegistry = relaxedMockk {
        every { timer(any(), *anyVararg()) } returns relaxedMockk {
            every { record(any<Runnable>()) } answers { (firstArg() as Runnable).run() }
        }
    }

    @Nested
    @DisplayName("When publishing events")
    inner class PublishingEvents {
        /**
         * Copied from Micrometer unit test for [io.micrometer.elastic.ElasticMeterRegistry.countCreatedItems].
         */
        @Test
        fun `should count the right number of created items`() {
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val responseBody =
                "{\"took\":254,\"errors\":true,\"items\":[{\"index\":{\"_index\":\"events-2020-04-09\",\"_type\":\"doc\",\"_id\":\"VL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":4,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"events-2020-04-09\",\"_type\":\"doc\",\"_id\":\"Vb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":5,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"events-2020-04-09\",\"_type\":\"doc\",\"_id\":\"Vr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":2,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"events-2020-04-09\",\"_type\":\"doc\",\"_id\":\"V79-vGoBVqC16kvPZ54V\",\"status\":400,\"error\":{\"type\":\"illegal_argument_exception\",\"reason\":\"mapper [count] cannot be changed from type [float] to [long]\"}}},{\"index\":{\"_index\":\"events-2020-04-09\",\"_type\":\"doc\",\"_id\":\"WL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":8,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"events-2020-04-09\",\"_type\":\"doc\",\"_id\":\"Wb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":1,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"events-2020-04-09\",\"_type\":\"doc\",\"_id\":\"Wr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":1,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"events-2020-04-09\",\"_type\":\"doc\",\"_id\":\"W79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":6,\"_primary_term\":1,\"status\":201}}]}"

            assertThat(logger.countCreatedItems(responseBody)).isEqualTo(7)
        }

        @Test
        @Timeout(5)
        internal fun `should support concurrent publication`() {
            val receivedDocuments = AtomicInteger(0)
            val latch = CountDownLatch(9)
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        if (request.url.fullPath.endsWith("/_bulk")) {
                            val body = request.body as TextContent
                            receivedDocuments.addAndGet(body.text.count { it == '\n' } / 2)
                            val responseHeaders =
                                headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
                            latch.countDown()
                            respond("""
                        {
                           "took": 30,
                           "errors": false,
                           "items": []
                        }
                    """.trimIndent(), headers = responseHeaders)
                        } else {
                            respondOk()
                        }
                    }
                }
            }

            val configuration = ElasticsearchEventLoggerConfiguration(
                urls = listOf("http://elasticsearch-1:9200", "http://elasticsearch-2:9200",
                    "http://elasticsearch-3:9200"),
                batchSize = 100, lingerPeriod = Duration.ofMinutes(1), publishers = 3
            )
            val logger = ElasticsearchEventsLogger(configuration, meterRegistry, client)
            logger.start()

            val job1 = GlobalScope.launch {
                repeat(300) {
                    logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
                }
            }
            val job2 = GlobalScope.launch {
                repeat(300) {
                    logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
                }
            }

            runBlocking {
                repeat(300) {
                    logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
                }
                job1.join()
                job2.join()
            }

            latch.await()
            Assertions.assertEquals(900, receivedDocuments.get())
        }

        @Test
        @Timeout(1)
        internal fun `should balance the publications on all the nodes`() {
            val receivedUrls = Sets.newConcurrentHashSet<String>()
            val latch = CountDownLatch(20)
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        receivedUrls.add(request.url.hostWithPort)
                        latch.countDown()
                        respond("{\"took\": 30,\"errors\": false,\"items\": []}")
                    }
                }
            }

            val urls = listOf("elasticsearch-1:9200", "elasticsearch-2:9200", "elasticsearch-3:9200")
            val configuration = ElasticsearchEventLoggerConfiguration(
                urls = urls.map { "http://$it" },
                batchSize = 1, lingerPeriod = Duration.ofMinutes(1), publishers = 3
            )
            val logger = ElasticsearchEventsLogger(configuration, meterRegistry, client)
            logger.start()

            repeat(20) {
                logger.warn(EVENT_NAME, EVENT_VALUE, EVENT_TAGS_MAP)
            }

            latch.await()

            // All the nodes were used.
            Assertions.assertEquals(setOf(*urls.toTypedArray()), receivedUrls)
        }
    }

    @Nested
    @DisplayName("When converting events")
    inner class EventConversion {

        @Test
        fun `should write document with tags`() {
            val event =
                Event("my-event", EventLevel.INFO, listOf(EventTag("key-1", "value-1"), EventTag("key-2", "value-2")),
                    null)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"tags\":{\"key-1\":\"value-1\",\"key-2\":\"value-2\"}}",
                result, false)
        }

        @Test
        fun `should write document without tags`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), null)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals(
                "{\"@timestamp\":\"${expectedTimestamp(event)}\",\"name\":\"my-event\",\"level\":\"info\"}",
                result,
                false)
        }

        @Test
        fun `should write document with value as string`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), "my-message")
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"message\":\"my-message\"}", result, false)
        }

        @Test
        fun `should write document with value as boolean`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), true)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"boolean\":true}", result, false)
        }

        @Test
        fun `should write document with value as finite double`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), 123.65)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"number\":123.65}", result, false)
        }

        @Test
        fun `should write document with value as positive infinite double`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), Double.POSITIVE_INFINITY)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"non-finite-decimal\":\"Infinity\"}", result,
                false)
        }

        @Test
        fun `should write document with value as negative infinite double`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), Double.NEGATIVE_INFINITY)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"non-finite-decimal\":\"-Infinity\"}", result,
                false)
        }

        @Test
        fun `should write document with value as NaN double`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), Double.NaN)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"non-finite-decimal\":\"NaN\"}", result,
                false)
        }

        @Test
        fun `should write document with value as float`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), 123.65.toFloat())
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"number\":123.65}", result, false)
        }

        @Test
        fun `should write document with value as big decimal`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), 123.65.toBigDecimal())
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"number\":123.65}", result, false)
        }

        @Test
        fun `should write document with value as integer`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), 123)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"number\":123}", result, false)
        }

        @Test
        fun `should write document with value as big integer`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), 123.toBigInteger())
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"number\":123}", result, false)
        }

        @Test
        fun `should write document with value as long`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), 123.toLong())
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"number\":123}", result, false)
        }

        @Test
        fun `should write document with value as instant`() {
            val now = Instant.now()
            val event = Event("my-event", EventLevel.INFO, emptyList(), now)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"instant\":\"${DateTimeFormatter.ISO_INSTANT.format(
                now)}\"}", result, false)
        }

        @Test
        fun `should write document with value as zoned date time`() {
            val now = ZonedDateTime.now()
            val event = Event("my-event", EventLevel.INFO, emptyList(), now)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"instant\":\"${DateTimeFormatter.ISO_INSTANT.format(
                now)}\"}", result, false)
        }

        @Test
        fun `should write document with value as local date time`() {
            val now = LocalDateTime.now()
            val event = Event("my-event", EventLevel.INFO, emptyList(), now)
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"instant\":\"${DateTimeFormatter.ISO_INSTANT.format(
                now.atZone(ZoneId.systemDefault()))}\"}", result, false)
        }

        @Test
        fun `should write document with value as throwable`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), relaxedMockk<Throwable> {
                every { message } returns "my-error"
                every { printStackTrace(any<PrintWriter>()) } answers {
                    (firstArg() as PrintWriter).write("this is the stack")
                }
            })
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"error\":\"my-error\",\"stack-trace\":\"this is the stack\"}",
                result, false)
        }

        @Test
        fun `should write document with value as duration to millis`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), Duration.ofSeconds(12))
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"duration\":12000,\"unit\":\"milliseconds\"}",
                result, false)
        }

        @Test
        fun `should write document with value as duration to nanos`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), Duration.ofSeconds(12))
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(durationAsNano = true), meterRegistry,
                    relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"duration\":12000000000,\"unit\":\"nanoseconds\"}",
                result, false)
        }

        @Test
        fun `should write document with value as geo point`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), EventGeoPoint(12.34, 34.76))
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"latitude\":12.34,\"longitude\":34.76}",
                result,
                false)
        }

        @Test
        fun `should write document with value as range`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), EventRange(12.34, 34.76))
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"lower-bound\":12.34,\"upper-bound\":34.76}",
                result, false)
        }

        @Test
        fun `should write document with value as array`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), arrayOf(12.34, "here is the test"))
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"values\":[\"12.34\",\"here is the test\"]}",
                result, false)
        }

        @Test
        fun `should write document with value as iterable`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), listOf(12.34, "here is the test"))
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"values\":[\"12.34\",\"here is the test\"]}",
                result, false)
        }

        @Test
        fun `should write document with value as any object`() {
            val event = Event("my-event", EventLevel.INFO, emptyList(), MyTestObject())
            val logger =
                ElasticsearchEventsLogger(ElasticsearchEventLoggerConfiguration(), meterRegistry, relaxedMockk { })
            val result = logger.writeDocument(event)

            JSONAssert.assertEquals("{\"@timestamp\":\"${expectedTimestamp(
                event)}\",\"name\":\"my-event\",\"level\":\"info\",\"value\":\"{\\\"property1\\\":1243.65,\\\"property2\\\":\\\"here is the test\\\"}\"}",
                result, false)
        }


        private fun expectedTimestamp(event: Event) =
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(event.timestamp))
    }

    data class MyTestObject(val property1: Double = 1243.65, val property2: String = "here is the test")

    companion object {

        const val EVENT_NAME = "event-name"

        const val EVENT_VALUE = "my-value"

        val EVENT_TAGS_MAP = mapOf("key-1" to "value-1", "key-2" to "value-2")

    }
}
