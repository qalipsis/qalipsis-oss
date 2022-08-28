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
 * Converts each value of an array using the provided rules.
 * The index of the rules have to match the column to which the rule applies.
 *
 * @author Eric Jessé
 */
class ArrayDatasourceObjectProcessor(
    private val conversionsRules: Array<((Any?) -> Any?)?>
) : DatasourceObjectProcessor<Array<Any?>, Array<Any?>> {

    override fun process(offset: AtomicLong, readObject: Array<Any?>): Array<Any?> {
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
                log.debug(e) { "Row $offset, column $columnIndex, value $value: ${e.message}" }
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
