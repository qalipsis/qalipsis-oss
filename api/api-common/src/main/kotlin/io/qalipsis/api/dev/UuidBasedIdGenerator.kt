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

import io.qalipsis.api.lang.IdGenerator
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Implementation of [IdGenerator] using random [UUID]s.
 *
 * @author Eric Jessé
 */
@Singleton
class UuidBasedIdGenerator : IdGenerator {

    override fun long(): String {
        return UUID.randomUUID().toString().lowercase().replace("-", "")
    }

    override fun short(): String {
        return long().substring(22, 32)
    }

}
