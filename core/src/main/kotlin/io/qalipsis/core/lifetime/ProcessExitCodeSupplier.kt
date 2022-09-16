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

package io.qalipsis.core.lifetime

import io.micronaut.core.order.Ordered
import java.util.Optional

/**
 * Interfaces able to specify the exit code of the main process of QALIPSIS.
 *
 * @author Eric Jessé
 */
interface ProcessExitCodeSupplier : Ordered {

    /**
     * Await the service completion and returns a potential process exit code.
     * When no particular exit code has to be specified, an [Optional.empty] should be returned.
     */
    suspend fun await(): Optional<Int>
}