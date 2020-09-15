package io.evolue.plugins.jackson.csv

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.datasource.DatasourceException
import io.evolue.api.steps.datasource.DatasourceObjectProcessor

/**
 *
 * @author Eric Jessé
 */
class LinkedHashMapToListObjectProcessor(
        private val conversionsRules: List<((Any?) -> Any?)?>
) : DatasourceObjectProcessor<LinkedHashMap<String, Any?>, List<Any?>> {

    override fun process(offset: Long, readObject: LinkedHashMap<String, Any?>): List<Any?> {
        val errors = mutableListOf<String>()
        val result = arrayListOf<Any?>()
        readObject.entries.forEachIndexed { columnIndex, entry ->
            val value = entry.value
            try {
                result.add(
                        if (conversionsRules.size > columnIndex && conversionsRules[columnIndex] != null) {
                            conversionsRules[columnIndex]?.let { it(value) } ?: value
                        } else {
                            value
                        }
                )
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