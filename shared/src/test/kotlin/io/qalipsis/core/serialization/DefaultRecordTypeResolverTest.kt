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
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import io.qalipsis.api.serialization.SerializersProvider
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.untrusted.TRAPPED_TYPE_NAME
import io.qalipsis.untrusted.TrapState
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalSerializationApi::class)
internal class DefaultRecordTypeResolverTest {

    private val serializersProvider = SerializersProvider()

    private val resolver = DefaultRecordTypeResolver(serializersProvider, emptyList())

    @Test
    fun `given a type with a registered serializer when resolving then the type is returned`() {
        assertThat(resolver.resolve(EndOfCampaignFeedback::class.java.name)).isSameInstanceAs(EndOfCampaignFeedback::class.java)
    }

    @Test
    fun `given built-in value types when resolving then the types are returned`() {
        listOf(
            String::class.javaObjectType, Int::class.javaObjectType, Double::class.javaObjectType, Unit::class.java,
            Pair::class.java, Instant::class.java, java.math.BigDecimal::class.java, java.util.UUID::class.java,
            IntArray::class.java, Array<String>::class.java
        ).forEach { type ->
            assertThat(resolver.resolve(type.name)).isSameInstanceAs(type)
        }
    }

    @Test
    fun `given collections when resolving then the types are returned`() {
        listOf(ArrayList::class.java, LinkedHashMap::class.java, ConcurrentHashMap::class.java).forEach { type ->
            assertThat(resolver.resolve(type.name)).isSameInstanceAs(type)
        }
    }

    @Test
    fun `given a type of a collection package that is not a collection when resolving then it is rejected`() {
        assertThrows<RejectedRecordTypeException> { resolver.resolve(java.util.Timer::class.java.name) }
    }

    @Test
    fun `given types of not allowed packages when resolving then they are rejected`() {
        listOf(
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "javax.naming.InitialContext",
            "com.sun.rowset.JdbcRowSetImpl",
            "io.qalipsis.core.serialization.SerializationFactory",
            "[Ljava.lang.ProcessBuilder;"
        ).forEach { typeName ->
            assertThrows<RejectedRecordTypeException> { resolver.resolve(typeName) }
        }
    }

    @Test
    fun `given a type of a not allowed package when resolving then it is rejected and not initialized`() {
        assertThrows<RejectedRecordTypeException> { resolver.resolve(TRAPPED_TYPE_NAME) }

        assertThat(TrapState.initialized.get()).isFalse()
    }

    @Test
    fun `given a type of a configured package when resolving then it is returned without being initialized`() {
        val resolver = DefaultRecordTypeResolver(serializersProvider, listOf(" io.qalipsis.untrusted. ", ""))

        assertThat(resolver.resolve(TRAPPED_TYPE_NAME).name).isEqualTo(TRAPPED_TYPE_NAME)
        assertThat(TrapState.initialized.get()).isFalse()
    }

    @Test
    fun `given a blank configured package when resolving a type of another package then it is rejected`() {
        val resolver = DefaultRecordTypeResolver(serializersProvider, listOf("", " "))

        assertThrows<RejectedRecordTypeException> { resolver.resolve(TRAPPED_TYPE_NAME) }
    }
}
