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

package io.qalipsis.core.serialization

interface SerializationContext {

    val target: SerializationTarget

    companion object {

        val EMPTY: SerializationContext = object : SerializationContext {
            override val target = SerializationTarget.UNDEFINED
        }

        val CONTEXT: SerializationContext = object : SerializationContext {
            override val target = SerializationTarget.CONTEXT
        }
    }
}

interface DeserializationContext {

    val target: SerializationTarget

    companion object {

        val EMPTY: DeserializationContext = object : DeserializationContext {
            override val target = SerializationTarget.UNDEFINED
        }

        val CONTEXT: DeserializationContext = object : DeserializationContext {
            override val target = SerializationTarget.CONTEXT
        }
    }
}

enum class SerializationTarget {
    UNDEFINED,
    STATE_REGISTRY,
    CONTEXT
}