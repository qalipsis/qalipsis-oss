/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.serialization

import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.heartbeat.Heartbeat
import jakarta.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Serializer of the messages exchanged between the head and the factories to orchestrate the campaigns.
 *
 * The messages are encoded with the [ProtoBuf] format and the polymorphic subtypes registered in its serializers module,
 * so that only the closed set of known message types can be decoded: a message naming an unknown subtype is rejected
 * without any class being resolved by its name.
 *
 * @author Eric Jessé
 */
@Singleton
@OptIn(ExperimentalSerializationApi::class)
class ControlPlaneSerializer(
    protoBuf: ProtoBuf
) {

    // ProtoBuf cannot encode null values, so the properties equal to their (null) default are omitted.
    private val protoBuf = ProtoBuf(from = protoBuf) { encodeDefaults = false }

    private val directiveSerializer = PolymorphicSerializer(Directive::class)

    private val feedbackSerializer = PolymorphicSerializer(Feedback::class)

    fun serialize(entity: Directive): ByteArray = protoBuf.encodeToByteArray(directiveSerializer, entity)

    fun serialize(entity: Feedback): ByteArray = protoBuf.encodeToByteArray(feedbackSerializer, entity)

    fun serialize(entity: HandshakeRequest): ByteArray =
        protoBuf.encodeToByteArray(HandshakeRequest.serializer(), entity)

    fun serialize(entity: HandshakeResponse): ByteArray =
        protoBuf.encodeToByteArray(HandshakeResponse.serializer(), entity)

    fun serialize(entity: Heartbeat): ByteArray = protoBuf.encodeToByteArray(Heartbeat.serializer(), entity)

    fun deserializeDirective(source: ByteArray): Directive = protoBuf.decodeFromByteArray(directiveSerializer, source)

    fun deserializeFeedback(source: ByteArray): Feedback = protoBuf.decodeFromByteArray(feedbackSerializer, source)

    /**
     * Deserializes a [Feedback] that has to be a [CampaignMetersFeedback].
     *
     * @throws SerializationException when the message is another kind of [Feedback]
     */
    fun deserializeMetersFeedback(source: ByteArray): CampaignMetersFeedback =
        deserializeFeedback(source) as? CampaignMetersFeedback
            ?: throw SerializationException("The received feedback is not a ${CampaignMetersFeedback::class.simpleName}")

    fun deserializeHandshakeRequest(source: ByteArray): HandshakeRequest =
        protoBuf.decodeFromByteArray(HandshakeRequest.serializer(), source)

    fun deserializeHandshakeResponse(source: ByteArray): HandshakeResponse =
        protoBuf.decodeFromByteArray(HandshakeResponse.serializer(), source)

    fun deserializeHeartbeat(source: ByteArray): Heartbeat = protoBuf.decodeFromByteArray(Heartbeat.serializer(), source)
}
