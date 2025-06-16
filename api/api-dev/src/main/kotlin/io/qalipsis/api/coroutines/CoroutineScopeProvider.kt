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

package io.qalipsis.api.coroutines

import kotlinx.coroutines.CoroutineScope

/**
 * Provider of [CoroutineScope] for the different kinds of operations in QALIPSIS.
 *
 * @author Eric Jessé
 */
interface CoroutineScopeProvider {

    /**
     * Scope for global operations.
     */
    val global: CoroutineScope

    /**
     * Scope for execution of the scenarios.
     */
    val campaign: CoroutineScope

    /**
     * Scope for execution of the network operations.
     */
    val io: CoroutineScope

    /**
     * Scope for the background tasks.
     */
    val background: CoroutineScope

    /**
     * Scope for the orchestration tasks.
     */
    val orchestration: CoroutineScope

    fun close()
}