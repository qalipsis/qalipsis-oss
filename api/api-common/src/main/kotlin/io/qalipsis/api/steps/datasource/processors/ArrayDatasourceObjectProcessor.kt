package io.qalipsis.api.steps.datasource.processors

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceException
import io.qalipsis.api.steps.datasource.DatasourceObjectProcessor

/**
 * Converts each value of an array using the provided rules.
 * The index of the rules have to match the column to which the rule applies.
 *
 * @author Eric Jessé
 */
class ArrayDatasourceObjectProcessor(
        private val conversionsRules: Array<((Any?) -> Any?)?>
) : DatasourceObjectProcessor<Array<Any?>, Array<Any?>> {

    override fun process(offset: Long, readObject: Array<Any?>): Array<Any?> {
        val errors = mutableListOf<String>()
        val result = arrayOfNulls<Any?>(readObject.size)
        readObject.forEachIndexed { columnIndex, value ->
            try {
                result[columnIndex] =
                    if (conversionsRules.size > columnIndex && conversionsRules[columnIndex] != null) {
                        conversionsRules[columnIndex]?.let { it(value) } ?: value
                    } else {
                        value
                    }
            } catch (e: Exception) {
                log.debug("Row $offset, column $columnIndex, value $value: ${e.message}", e)
                errors.add("column $columnIndex, value $value: ${e.message}")
            }
        }
        if (errors.isNotEmpty()) {
            throw DatasourceException(errors.joinToString())
        }
        return result
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
