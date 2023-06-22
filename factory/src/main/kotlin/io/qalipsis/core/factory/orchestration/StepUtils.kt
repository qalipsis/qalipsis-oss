/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepSpecification
import org.apache.commons.lang3.StringUtils

internal object StepUtils {

    /**
     * Resolves the type group of a step specification.
     */
    val StepSpecification<*, *, *>.type: String
        get() {
            val className =
                this::class.java.canonicalName.substringAfterLast(".").replace(Regex("(Step)?Specification.*$"), "")
            return className.let { StringUtils.splitByCharacterTypeCamelCase(it).joinToString("-") { it.lowercase() } }
        }

    /**
     * Resolves the type group of a step.
     */
    val Step<*, *>.type: String
        get() = if (this is StepDecorator<*, *>) {
            this.decorated.type
        } else {
            this::class.java.canonicalName.substringAfterLast(".").substringBefore("Step")
        }.let { StringUtils.splitByCharacterTypeCamelCase(it).joinToString("-") { it.lowercase() } }

    /**
     * Verifies whether the step only exists for technical reason and should not be seen from the users.
     */
    val Step<*, *>.isHidden: Boolean
        get() = this.name.startsWith("__")

    /**
     * Verifies whether the step was explicitly named by a user or not.
     */
    val Step<*, *>.isAnonymous: Boolean
        get() = this.name.startsWith("_")
}