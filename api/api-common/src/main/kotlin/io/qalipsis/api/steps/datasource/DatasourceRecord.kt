package io.qalipsis.api.steps.datasource

/**
 * Record from generated from a line of a CSV file, containing the values as a POJO.
 *
 * @property ordinal the ordinal of the record in the whole file.
 * @property value POJO containing the value of a line.
 *
 * @author Eric Jessé
 */
data class DatasourceRecord<O : Any?>(
        val ordinal: Long,
        val value: O
)