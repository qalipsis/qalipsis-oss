/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
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