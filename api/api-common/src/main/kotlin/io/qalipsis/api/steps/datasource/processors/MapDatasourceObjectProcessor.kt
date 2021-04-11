package io.qalipsis.api.steps.datasource.processors

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceException
import io.qalipsis.api.steps.datasource.DatasourceObjectProcessor
import java.util.concurrent.atomic.AtomicLong

/**
 * Converts each value of a map using the provided rules.
 * The key of the rules have to match the one in the map to which the rule applies.
 *
 * @author Eric Jessé
 */
class MapDatasourceObjectProcessor(
    private val conversionsRules: Map<String, ((Any?) -> Any?)?>
) : DatasourceObjectProcessor<Map<String, Any?>, Map<String, Any?>> {

    override fun process(offset: AtomicLong, readObject: Map<String, Any?>): Map<String, Any?> {
        val errors = mutableListOf<String>()
        val result = readObject.mapValues { entry ->
            val value = entry.value
            try {
                conversionsRules[entry.key]?.let { it(value) } ?: value
            } catch (e: Exception) {
                log.debug("Row $offset, field $entry.key, value $value: ${e.message}", e)
                errors.add("column ${entry.key}, value $value: ${e.message}")
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
