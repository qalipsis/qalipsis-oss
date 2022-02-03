package io.qalipsis.api.steps.datasource

/**
 * Record issued from the extraction of a file or database table, representing a single value / item.
 *
 * @property ordinal the ordinal of the record in the whole set.
 * @property value record or value.
 *
 * @author Eric Jessé
 */
data class DatasourceRecord<T : Any?>(
    val ordinal: Long,
    val value: T
)