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

package io.qalipsis.core.factory.steps.singleton

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepReportingSpecification
import io.qalipsis.api.steps.StepSpecification
import java.time.Duration

/**
 * Specification for a [io.qalipsis.api.steps.Step] running behind a singleton.
 *
 *
 * @property topic topic to transport the data from the singleton step to another one
 * @param T type ot of the output of the decorated step.
 *
 * @author Eric Jessé
 */
open class SingletonProxyStepSpecification<T>(
    val singletonStepName: StepName,
    val next: StepSpecification<T, *, *>,
    val topic: Topic<T>,
) : StepSpecification<T, T, SingletonProxyStepSpecification<T>> {

    override var name: StepName = singletonStepName

    override var scenario: StepSpecificationRegistry = next.scenario

    override var timeout: Duration? = null

    override val iterations: Long = 0

    override val iterationPeriods: Duration = Duration.ZERO

    override val stopIterationsOnError: Boolean = false

    override var retryPolicy: RetryPolicy? = null

    override var directedAcyclicGraphName: DirectedAcyclicGraphName = next.directedAcyclicGraphName

    override val nextSteps: MutableList<StepSpecification<*, *, *>>
        get() = mutableListOf(next)

    override var tags = mutableMapOf<String, String>()

    override fun split(block: StepSpecification<T, T, *>.() -> Unit): SingletonProxyStepSpecification<T> {
        // Nothing to do.
        return this
    }

    override fun add(step: StepSpecification<*, *, *>) {
        // Nothing to do.
    }

    override var reporting: StepReportingSpecification = StepReportingSpecification()

    override fun tag(tags: Map<String, String>) {
        this.tags.clear()
        this.tags += tags
    }
}
