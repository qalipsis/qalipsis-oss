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

package io.qalipsis.core.collections

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.prop
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

internal class LingerCollectionTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should add the elements`() = testDispatcherProvider.run {
        // given
        val collection = LingerCollection<Int>(10_000, Duration.ofMinutes(1)) {}

        // when
        collection.add(1)
        collection.add(10)
        collection.add(100)

        // then
        assertThat(collection).containsExactly(1, 10, 100)

        // when
        collection.addAll(listOf(2, 20, 200))

        // then
        assertThat(collection).containsExactly(1, 10, 100, 2, 20, 200)
    }

    @Test
    @Timeout(3)
    internal fun `should release when the time passes`() = testDispatcherProvider.run {
        // given
        val elements = (1..1000).toList()
        val received = concurrentList<List<Int>>()
        val publicationCounter = SuspendedCountLatch(1, true)
        val collection = LingerCollection<Int>(10_000, Duration.ofMillis(200)) {
            received += it
            publicationCounter.decrement()
        }

        // when
        collection.addAll(elements)

        // then
        assertThat(collection).containsExactly(*elements.toTypedArray())

        // when
        publicationCounter.await()

        // then
        assertThat(collection).isEmpty()
        assertThat(received[0]).containsExactly(*elements.toTypedArray())

        // when
        publicationCounter.reset()
        collection.addAll(elements)
        delay(350)
        publicationCounter.await()

        // when
        assertThat(received[1]).containsExactly(*elements.toTypedArray())
    }

    @Test
    @Timeout(1)
    internal fun `should release when the size is reached`() = testDispatcherProvider.run {
        // given
        val elements = (1..1000).toList()
        val received = concurrentList<List<Int>>()
        val publicationCounter = SuspendedCountLatch(2)
        val collection = LingerCollection<Int>(400, Duration.ofMinutes(1)) {
            received += it
            publicationCounter.decrement()
        }

        // when
        elements.forEach {
            collection.add(it)
        }
        publicationCounter.await()

        // then
        assertThat(received).all {
            hasSize(2)
            index(0).containsExactly(*(1..400).toList().toTypedArray())
            index(1).containsExactly(*(401..800).toList().toTypedArray())
        }
        assertThat(collection).prop(LingerCollection<Int>::size).isBetween(1, 200)
    }

    @Test
    @Timeout(1)
    internal fun `should release when the size is reached with a single add several times`() =
        testDispatcherProvider.run {
            // given
            val elements = (1..1000).toList()
            val received = concurrentList<List<Int>>()
            val publicationCounter = SuspendedCountLatch(2)
            val collection = LingerCollection<Int>(400, Duration.ofMinutes(1)) {
                received += it
                publicationCounter.decrement()
            }

            // when
            collection.addAll(elements)
            publicationCounter.await()

            // then
            assertThat(received).all {
                hasSize(2)
                index(0).containsExactly(*(1..400).toList().toTypedArray())
                index(1).containsExactly(*(401..800).toList().toTypedArray())
            }
            assertThat(collection).containsExactly(*(801..1000).toList().toTypedArray())

            // when
            received.clear()
            publicationCounter.reset()
            collection.addAll((1..700).toList())
            publicationCounter.await()

            // then
            assertThat(received).all {
                hasSize(2)
                index(0).containsExactly(*((801..1000).toList() + (1..200).toList()).toTypedArray())
                index(1).containsExactly(*(201..600).toList().toTypedArray())
            }
            assertThat(collection).containsExactly(*(601..700).toList().toTypedArray())

            // when
            received.clear()
            publicationCounter.reset()
            publicationCounter.decrement()
            collection.addAll((1..400).toList())
            publicationCounter.await()

            // then
            assertThat(received).all {
                hasSize(1)
                index(0).containsExactly(*((601..700).toList() + (1..300).toList()).toTypedArray())
            }
            assertThat(collection).containsExactly(*(301..400).toList().toTypedArray())
        }
}