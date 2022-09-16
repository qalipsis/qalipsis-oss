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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.factory.context.StepContextImpl

/**
 * Step to consume data without producing anything.
 *
 * @author Eric Jessé
 */
internal open class BlackHoleStep<I>(
    id: StepName
) : AbstractStep<I, Unit>(id, null) {

    override suspend fun execute(context: StepContext<I, Unit>) {
        if (context is StepContextImpl) {
            context.output.close()
        }
        context.receive()
    }
}
