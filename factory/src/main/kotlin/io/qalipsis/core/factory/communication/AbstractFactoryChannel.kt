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

package io.qalipsis.core.factory.communication

import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.DispatcherChannel

abstract class AbstractFactoryChannel(
    private val directiveRegistry: DirectiveRegistry
) : FactoryChannel {

    override val subscribedHandshakeResponseChannels = mutableSetOf<DispatcherChannel>()

    override val subscribedDirectiveChannels = mutableSetOf<DispatcherChannel>()

    protected lateinit var currentDirectiveBroadcastChannel: DispatcherChannel

    protected lateinit var currentFeedbackChannel: DispatcherChannel

    override suspend fun publishDirective(directive: Directive) {
        val channel = directive.channel.takeIf(String::isNotBlank) ?: currentDirectiveBroadcastChannel
        publishDirective(channel, directiveRegistry.prepareBeforeSend(channel, directive))
    }
}