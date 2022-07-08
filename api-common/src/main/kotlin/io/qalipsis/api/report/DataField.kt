package io.qalipsis.api.report

/**
 * Field of a data source.
 *
 * @author Eric Jessé
 *
 * @property name name of the field in the data source
 * @property number specifies whether the field can be used in aggregations
 * @property unit the unit of the values, if relevant (durations,...)
 */
data class DataField(
    val name: String,
    val number: Boolean,
    val unit: String? = null
)
