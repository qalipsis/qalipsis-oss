/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.collections

/**
 * Mutable [Table] designed to support concurrency.
 *
 * @author Eric Jessé
 */
interface ConcurrentTable<R, C, V> : MutableTable<R, C, V> {

    /**
     * Returns the value corresponding to the given [row] and [column], or uses the [supplier] to create the value
     * if it does not exist.
     *
     * The access to [row] and [column] are synchronized among all the caller.
     */
    fun computeIfAbsent(row: R, column: C, supplier: (C) -> V): V

}