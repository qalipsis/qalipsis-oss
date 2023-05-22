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

/**
 * Registry responsible for hosting and publishing the persisted directives.
 *
 * @author Eric Jessé
 */
interface DirectiveRegistry {

    suspend fun prepareBeforeSend(channel: DispatcherChannel, directive: Directive): Directive {
        return if (directive is SingleUseDirective<*>) {
            save(channel, directive)
        } else {
            directive
        }
    }

    suspend fun prepareAfterReceived(directive: Directive): Directive? {
        return if (directive is SingleUseDirectiveReference) {
            get(directive)
        } else {
            directive
        }
    }

    /**
     * Persist a [Directive] into the registry.
     */
    suspend fun save(channel: DispatcherChannel, directive: SingleUseDirective<*>): SingleUseDirectiveReference

    /**
     * Fetches and deletes the value of the [SingleUseDirective] if it was not yet read.
     */
    suspend fun <T : SingleUseDirectiveReference> get(reference: T): Directive?

}
