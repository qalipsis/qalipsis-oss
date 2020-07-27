package io.evolue.core.factory.events.elasticsearch

import io.evolue.api.events.EventLevel
import io.micronaut.context.annotation.ConfigurationProperties
import java.time.Duration
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

/**
 * Configuration for [ElasticsearchEventsLogger].
 *
 * @property loggableLevel minimal accepted level of events, default: INFO.
 * @property urls list of URLs to the Elasticsearch instances, default: http://localhost:9200.
 * @property indexPrefix prefix to use for the created indices containing the events, default: evolue-events.
 * @property indexDatePattern format of the date part of the index, as supported by [java.time.format.DateTimeFormatter], default: uuuu-MM-dd to create an index per day.
 * @property durationAsNano writes the duration as nanoseconds, default: false to write them as milliseconds.
 * @property lingerPeriod maximal period between two publication of events to Elasticsearch, default: 10 seconds.
 * @property batchSize maximal number of events buffered between two publication of events to Elasticsearch, default: 2000.
 * @property publishers number of concurrent publication of events that can be run, default: 1 (no concurrency).
 * @property username name of the user to use for basic authentication when connecting to Elasticsearch.
 * @property password password of the user to use for basic authentication when connecting to Elasticsearch.
 * @property shards number of shards to apply on the created indices for events, default: 1.
 * @property replicas number of replicas to apply on the created indices for events, default: 0.
 * @property proxy URL of the http proxy to use to access to Elasticsearch, it might be convenient in order to support other kind of authentication in Elasticsearch.
 *
 * @author Eric Jessé
 */
@ConfigurationProperties("events.export.elasticsearch")
data class ElasticsearchEventLoggerConfiguration(
    @NotNull
    val loggableLevel: EventLevel = EventLevel.INFO,
    @NotEmpty
    val urls: List<@NotBlank String> = listOf("http://localhost:9200"),
    @NotBlank
    val indexPrefix: String = "evolue-events",
    @NotBlank
    val indexDatePattern: String = "yyyy-MM-dd",
    @NotNull
    val durationAsNano: Boolean = false,
    @NotNull
    val lingerPeriod: Duration = Duration.ofSeconds(10),
    @Min(1)
    val batchSize: Int = 2000,
    @Min(1)
    val publishers: Int = 1,
    val username: String? = null,
    val password: String? = null,
    @Min(1)
    val shards: Int = 1,
    @Min(0)
    val replicas: Int = 0,
    val proxy: String? = null
)
