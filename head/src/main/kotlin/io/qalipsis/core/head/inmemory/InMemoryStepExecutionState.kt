/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.inmemory

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory tracking state for a single step within a scenario execution.
 *
 * @author Eric Jessé
 */
data class InMemoryStepExecutionState(
    val name: StepName,
    val dagId: DirectedAcyclicGraphName,
    val isUnderLoad: Boolean,
    val initialized: Boolean,
    val initializationError: String? = null
) {
    val successfulExecutionsCounter = AtomicLong()
    val failedExecutionsCounter = AtomicLong()
}
