package io.qalipsis.api.report

/**
 * Field of a time-series data source.
 *
 * @author Eric Jessé
 *
 * @property name name of the field in the data source
 * @property type type for the
 * @property unit the unit of the values, if relevant (durations,...) and not specified in the records
 */
data class DataField(
    val name: String,
    val type: DataFieldType,
    val unit: String? = null
)

/**
 * Type of a field of time-series data.
 */
enum class DataFieldType {
    STRING, NUMBER, BOOLEAN, OBJECT, DATE
}
