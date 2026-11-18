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
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.ErrorProcessingStep

/**
 * Step in charge of processing the errors received from the ancestors.
 *
 * This step is bypassed if there are no errors.
 *
 * @author Eric Jessé
 */
class CatchErrorsStep<I>(
    id: StepName,
    private val block: ((errors: Collection<StepError>) -> Unit)
) : AbstractStep<I, I>(id, null), ErrorProcessingStep<I, I> {

    @Throws(Throwable::class)
    override suspend fun execute(context: StepContext<I, I>) {
        if (context.errors.isNotEmpty()) {
            log.trace { "${context.errors.size} error(s) to be caught" }
            this.block(context.errors)
        }

        if (context.hasInput) {
            context.send(context.receive())
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
