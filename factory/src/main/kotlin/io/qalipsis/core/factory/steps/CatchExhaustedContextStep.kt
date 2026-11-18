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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep

/**
 * Step in charge of processing the context once it is exhausted.
 *
 * This step is bypassed if the context is not exhausted.
 *
 * @author Eric Jessé
 */
class CatchExhaustedContextStep<O>(
    id: StepName,
    private val block: (suspend (context: StepContext<*, O>) -> Unit)
) : AbstractStep<Any?, O>(id, null) {


    override suspend fun execute(context: StepContext<Any?, O>) {
        if (context.isExhausted) {
            log.trace { "Catching exhausted context" }
            this.block(context)
        } else if (context.hasInput) {
            @Suppress("UNCHECKED_CAST")
            context.send(context.receive() as O)
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
