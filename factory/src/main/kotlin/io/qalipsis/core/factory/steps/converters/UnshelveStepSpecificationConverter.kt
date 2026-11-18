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

package io.qalipsis.core.factory.steps.converters

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.UnshelveStepSpecification
import io.qalipsis.core.factory.steps.SingularUnshelveStep
import io.qalipsis.core.factory.steps.UnshelveStep

/**
 * [StepSpecificationConverter] from [UnshelveStepSpecification] to [UnshelveStep] and [SingularUnshelveStep].
 *
 * @author Eric Jessé
 */
@StepConverter
class UnshelveStepSpecificationConverter(
    private val sharedStateRegistry: SharedStateRegistry
) : StepSpecificationConverter<UnshelveStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is UnshelveStepSpecification<*, *>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<UnshelveStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as UnshelveStepSpecification<I, O>
        val step = if (spec.singular) {
            SingularUnshelveStep<I, O>(spec.name, sharedStateRegistry, spec.names.first(),
                spec.delete)
        } else {
            UnshelveStep<I>(spec.name, sharedStateRegistry, spec.names, spec.delete)
        }
        creationContext.createdStep(step)
    }

}
