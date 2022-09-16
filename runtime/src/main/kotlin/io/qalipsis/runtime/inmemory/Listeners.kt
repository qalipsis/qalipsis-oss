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

package io.qalipsis.runtime.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.HandshakeResponseListener
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.communication.HeartbeatListener
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class Listeners(
    val directiveListeners: Collection<DirectiveListener<*>>,
    val handshakeResponseListeners: Collection<HandshakeResponseListener>,
    val feedbackListeners: Collection<FeedbackListener<*>>,
    val handshakeRequestListeners: Collection<HandshakeRequestListener>,
    val heartbeatListeners: Collection<HeartbeatListener>
)