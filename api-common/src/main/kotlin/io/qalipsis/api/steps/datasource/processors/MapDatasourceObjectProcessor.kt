/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

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
                log.debug(e) { "Row $offset, field $entry.key, value $value: ${e.message}" }
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
