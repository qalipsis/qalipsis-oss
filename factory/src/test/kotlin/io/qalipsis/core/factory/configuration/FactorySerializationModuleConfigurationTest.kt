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

package io.qalipsis.core.factory.configuration

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.factory.orchestration.TransportableStepContext
import io.qalipsis.core.serialization.ControlPlaneSerializer
import io.qalipsis.core.serialization.RegisteredTypesSamples
import io.qalipsis.core.serialization.SerializationFactory
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
internal class FactorySerializationModuleConfigurationTest {

    private val protoBuf = SerializationFactory().protobuf(listOf(FactorySerializationModuleConfiguration()))

    private val serializer = ControlPlaneSerializer(protoBuf)

    private val samples = RegisteredTypesSamples(protoBuf.serializersModule)

    private val samplesWithNulls = RegisteredTypesSamples(protoBuf.serializersModule, nullableAsNull = true)

    @Test
    fun `given each directive registered in the factory when round-tripping then the directive is restored`() {
        val subclasses = samples.concreteSubclassesOf(Directive::class)
        assertThat(subclasses.filter { it == TransportableStepContext::class }).isNotEmpty()

        val failures = listOf(samples, samplesWithNulls).flatMap { samplesVariant ->
            subclasses.map { samplesVariant to it }
        }.mapNotNull { (samplesVariant, subclass) ->
            runCatching {
                val directive = samplesVariant.sample(subclass) as Directive
                val serialized = serializer.serialize(directive)
                val result = serializer.deserializeDirective(serialized)
                assertThat(result).isInstanceOf(subclass)
                assertThat(serializer.serialize(result).toList()).isEqualTo(serialized.toList())
            }.exceptionOrNull()?.let { "${subclass.qualifiedName}: $it" }
        }

        assertThat(failures).isEmpty()
    }
}
