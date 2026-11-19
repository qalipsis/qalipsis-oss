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

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.prop
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.heartbeat.Heartbeat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class)
internal class ControlPlaneSerializerTest {

    private val protoBuf = SerializationFactory().protobuf()

    private val serializer = ControlPlaneSerializer(protoBuf)

    private val samples = RegisteredTypesSamples(protoBuf.serializersModule)

    private val samplesWithNulls = RegisteredTypesSamples(protoBuf.serializersModule, nullableAsNull = true)

    @Test
    fun `given each registered directive when round-tripping then the directive is restored`() {
        val failures = roundTripAll(Directive::class, serializer::serialize, serializer::deserializeDirective)

        assertThat(failures).isEmpty()
    }

    @Test
    fun `given each registered feedback when round-tripping then the feedback is restored`() {
        val failures = roundTripAll(Feedback::class, serializer::serialize, serializer::deserializeFeedback)

        assertThat(failures).isEmpty()
    }

    @Test
    fun `given a meters feedback when round-tripping as meters feedback then the feedback is restored`() {
        val feedback = samples.sample(CampaignMetersFeedback::class)

        val result = serializer.deserializeMetersFeedback(serializer.serialize(feedback))

        assertThat(serializer.serialize(result)).isEqualTo(serializer.serialize(feedback))
    }

    @Test
    fun `given another feedback when deserializing as meters feedback then it is rejected`() {
        val bytes = serializer.serialize(samples.sample(EndOfCampaignFeedback::class))

        assertThrows<SerializationException> { serializer.deserializeMetersFeedback(bytes) }
    }

    @Test
    fun `given a handshake request when round-tripping then the request is restored`() {
        val request = samples.sample(HandshakeRequest::class)

        assertThat(serializer.deserializeHandshakeRequest(serializer.serialize(request))).isEqualTo(request)
    }

    @Test
    fun `given a handshake response when round-tripping then the response is restored`() {
        val response = samples.sample(HandshakeResponse::class)

        assertThat(serializer.deserializeHandshakeResponse(serializer.serialize(response))).isEqualTo(response)
    }

    @Test
    fun `given a heartbeat when round-tripping then the heartbeat is restored`() {
        val heartbeat = samples.sample(Heartbeat::class)

        assertThat(serializer.deserializeHeartbeat(serializer.serialize(heartbeat))).isEqualTo(heartbeat)
    }

    @Test
    fun `given a directive of a not registered type when deserializing then it is rejected`() {
        val senderProtoBuf = ProtoBuf {
            serializersModule = protoBuf.serializersModule + SerializersModule {
                polymorphic(Directive::class) { subclass(TestDescriptiveDirective::class) }
            }
        }
        val bytes = senderProtoBuf.encodeToByteArray(PolymorphicSerializer(Directive::class), TestDescriptiveDirective())

        assertThrows<kotlinx.serialization.SerializationException> { serializer.deserializeDirective(bytes) }
    }

    /**
     * Round-trips a sample of every concrete subtype registered for [baseClass] and returns the descriptions
     * of the failures.
     */
    private fun <T : Any> roundTripAll(
        baseClass: KClass<T>,
        serialize: (T) -> ByteArray,
        deserialize: (ByteArray) -> T
    ): List<String> {
        val subclasses = samples.concreteSubclassesOf(baseClass)
        assertThat(subclasses).isNotEmpty()
        return listOf(samples, samplesWithNulls).flatMap { samplesVariant ->
            subclasses.map { samplesVariant to it }
        }.mapNotNull { (samplesVariant, subclass) ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val entity = samplesVariant.sample(subclass) as T
                val serialized = serialize(entity)
                val result = deserialize(serialized)
                assertThat(result).isInstanceOf(subclass)
                assertThat(result).prop("serialized form") { serialize(it).toList() }.isEqualTo(serialized.toList())
            }.exceptionOrNull()?.let { "${subclass.qualifiedName}: $it" }
        }
    }
}
