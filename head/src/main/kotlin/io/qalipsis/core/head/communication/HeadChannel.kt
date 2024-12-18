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

package io.qalipsis.core.head.communication

import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.lifetime.HeadStartupComponent

/**
 * Communication component in charge of consuming incoming messages to the head and dispatching outgoing ones.
 *
 * @author Eric Jessé
 */
interface HeadChannel : HeadStartupComponent {

    /**
     * Subscribes to the channels [channelNames] to consume the [io.qalipsis.core.handshake.HandshakeRequest]s.
     */
    fun subscribeHandshakeRequest(vararg channelNames: DispatcherChannel)

    /**
     * Closes the subscription to the channels [channelNames] for the [io.qalipsis.core.handshake.HandshakeRequest]s.
     */
    fun unsubscribeHandshakeRequest(vararg channelNames: DispatcherChannel)

    /**
     * Subscribes to the channels [channelNames] to consume the [io.qalipsis.core.feedbacks.Feedback]s.
     */
    fun subscribeFeedback(vararg channelNames: DispatcherChannel)

    /**
     * Closes the subscription to the channels [channelNames] for the [io.qalipsis.core.feedbacks.Feedback]s.
     */
    fun unsubscribeFeedback(vararg channelNames: DispatcherChannel)

    /**
     * Sends a [Directive] to the channel specified in [directive].
     */
    suspend fun publishDirective(directive: Directive)

    /**
     * Sends a serialized [Feedback] back to the heads.
     */
    suspend fun publishFeedback(channelName: DispatcherChannel, campaignKey: String, serializedFeedback: Any)

    /**
     * Sends a [HandshakeResponse] to a factory via the channel [channelName].
     */
    suspend fun publishHandshakeResponse(channelName: DispatcherChannel, handshakeResponse: HandshakeResponse)

}