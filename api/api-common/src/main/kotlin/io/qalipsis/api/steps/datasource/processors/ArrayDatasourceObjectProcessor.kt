/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
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
