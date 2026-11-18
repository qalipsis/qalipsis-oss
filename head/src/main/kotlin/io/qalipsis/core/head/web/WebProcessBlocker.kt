/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.inject.Singleton

/**
 * Blocker to keep the head process active when the web endpoints are enabled.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.AUTOSTART])
)
class WebProcessBlocker : ProcessBlocker {

    private val latch = Latch(true)

    override suspend fun join() {
        latch.await()
    }

    override fun cancel() {
        latch.cancel()
    }
}