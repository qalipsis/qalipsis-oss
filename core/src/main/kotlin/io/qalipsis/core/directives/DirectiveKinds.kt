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

package io.qalipsis.core.directives

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


typealias DirectiveKey = String
typealias DispatcherChannel = String

/**
 * A Directive is sent from the head to the factories to notify them of operations to perform.
 *
 * @property channel name of the channel to send this directive to, which defaults to the broadcast channel when blank
 */
@Serializable
@Polymorphic
abstract class Directive {

    @Transient
    open val channel: DispatcherChannel = ""
}

/**
 * Kind of [Directive] containing all the relevant information to process the directive.
 *
 */
@Serializable
abstract class DescriptiveDirective : Directive()

/**
 * Kind of [Directive] containing a key to read the actual directive from the cache.
 *
 */
@Serializable
abstract class DirectiveReference : Directive()

interface ReferencableDirective<T : DirectiveReference> {

    fun toReference(key: DirectiveKey): T
}

/**
 * Kind of [Directive] containing a value that can be read only once.
 * Those directives are stored in the [DirectiveRegistry] until they are read.
 *
 * They are published to the directive consumers as a [SingleUseDirectiveReference].
 */
@Serializable
abstract class SingleUseDirective<R : SingleUseDirectiveReference>(
) : Directive(), ReferencableDirective<R>

/**
 * Transportable representation of a [SingleUseDirective].
 */
@Serializable
abstract class SingleUseDirectiveReference : DirectiveReference() {
    abstract val key: DirectiveKey
}
