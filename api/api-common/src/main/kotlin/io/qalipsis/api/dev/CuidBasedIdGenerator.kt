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

package io.qalipsis.api.dev

import cool.graph.cuid.Cuid
import io.micronaut.context.annotation.Primary
import io.qalipsis.api.lang.IdGenerator
import jakarta.inject.Singleton

/**
 * Implementation of [IdGenerator] using random [Cuid]s.
 *
 * @author Eric Jessé
 */
@Primary
@Singleton
class CuidBasedIdGenerator : IdGenerator {

    override fun long(): String {
        return Cuid.createCuid()
    }

    override fun short(): String {
        return Cuid.createCuid().substring(15, 25).lowercase()
    }

}
