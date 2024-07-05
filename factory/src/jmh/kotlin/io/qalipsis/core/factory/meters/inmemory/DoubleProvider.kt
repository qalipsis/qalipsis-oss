/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters.inmemory

import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.atomic.AtomicInteger

@State(Scope.Thread)
class DoubleProvider {

    /**
     * Creates random durations between 0 and 10000 seconds.
     */
    private val values = List(BUFFER_SIZE) { Math.random() * 10000 }.shuffled()

    private var index = AtomicInteger(0)

    fun value() = values[getIndex()]

    private fun getIndex(): Int {
        return if (index.compareAndSet(BUFFER_SIZE, 0)) {
            // Reset the index.
            0
        } else {
            index.getAndIncrement()
        }
    }

    companion object {

        const val BUFFER_SIZE = 10000

    }

}