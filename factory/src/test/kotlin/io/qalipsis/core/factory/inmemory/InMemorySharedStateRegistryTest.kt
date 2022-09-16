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

package io.qalipsis.core.factory.inmemory

import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class InMemorySharedStateRegistryTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun shouldSetAndGet() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(123))

        registry.set(definition, "My value")

        // The key should ignore the time to live of the definition.
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        // The value can be read twice.
        assertEquals("My value", registry.get(SharedStateDefinition("minion-1", "state")))
        assertEquals("My value", registry.get(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldSetAndRemove() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(123))

        registry.set(definition, "My value")

        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        assertEquals("My value", registry.remove(SharedStateDefinition("minion-1", "state")))
        // The values cannot be read after removal.
        assertNull(registry.get(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldDeleteWhenSettingNull() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(123))

        registry.set(definition, "My value")

        // The key should ignore the time to live of the definition.
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        assertEquals("My value", registry.get(SharedStateDefinition("minion-1", "state")))

        // The value is now set to null
        registry.set(definition, null)
        assertNull(registry.get(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldSetAllAndGetAll() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-1", "state-2", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-1", "state-3", Duration.ofMillis(123))

        registry.set(mapOf(definition1 to "My value 1", definition2 to "My value 2", definition3 to null))

        // The key should ignore the time to live of the definition.
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state-1")))
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state-2")))
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state-3")))
        assertEquals("My value 1", registry.get(SharedStateDefinition("minion-1", "state-1")))
        assertEquals("My value 2", registry.get(SharedStateDefinition("minion-1", "state-2")))
        assertNull(registry.get(SharedStateDefinition("minion-1", "state-3")))

        val allValues = registry.get(listOf(definition1, definition2, definition3))
        assertEquals("My value 1", allValues["state-1"])
        assertEquals("My value 2", allValues["state-2"])
        assertNull(allValues["state-3"])
    }

    @Test
    internal fun shouldSetAllAndRemoveAll() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-1", "state-2", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-1", "state-3", Duration.ofMillis(123))

        registry.set(mapOf(definition1 to "My value 1", definition2 to "My value 2", definition3 to null))

        // The key should ignore the time to live of the definition.
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state-1")))
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state-2")))
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state-3")))
        assertEquals("My value 1", registry.get(SharedStateDefinition("minion-1", "state-1")))
        assertEquals("My value 2", registry.get(SharedStateDefinition("minion-1", "state-2")))
        assertNull(registry.get(SharedStateDefinition("minion-1", "state-3")))

        val allValues = registry.remove(listOf(definition1, definition2, definition3))
        assertEquals("My value 1", allValues["state-1"])
        assertEquals("My value 2", allValues["state-2"])
        assertNull(allValues["state-3"])

        // The values cannot be read after removal.
        assertNull(registry.get(SharedStateDefinition("minion-1", "state-1")))
        assertNull(registry.get(SharedStateDefinition("minion-1", "state-2")))
    }

    @Test
    internal fun shouldIsolateSameStateForDifferentMinions() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state")
        val definition2 = SharedStateDefinition("minion-2", "state")

        registry.set(definition1, "My value 1")
        registry.set(definition2, "My value 2")

        assertEquals("My value 1", registry.get(definition1))
        assertEquals("My value 2", registry.get(definition2))
    }

    @Test
    internal fun shouldIsolateDifferentStatesForSameMinion() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1")
        val definition2 = SharedStateDefinition("minion-1", "state-2")

        registry.set(definition1, "My value 1")
        registry.set(definition2, "My value 2")

        assertEquals("My value 1", registry.get(definition1))
        assertEquals("My value 2", registry.get(definition2))
    }

    @Test
    internal fun shouldBeEvictedAfterDefaultTimeToLive() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state")

        registry.set(definition, "My value")

        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(100)
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLive() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry.set(definition, "My value")

        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait just before the time to live.
        Thread.sleep(60)
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(60)
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLiveAfterWrite() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry.set(definition, "My value")

        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        // Wait just before the time to live.
        Thread.sleep(80)

        // The value is written again, the expiration should be updated.
        registry.set(SharedStateDefinition("minion-1", "state", Duration.ofMillis(200)), "My value")

        // Wait just before the time to live.
        Thread.sleep(100)
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(100)
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLiveAfterRead() = testDispatcherProvider.run {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry.set(definition, "My value")

        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        // Wait just before the time to live.
        Thread.sleep(80)

        // The value is read, the expiration should be updated.
        registry.get<String>(definition)

        // Wait just before the time to live.
        Thread.sleep(80)
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(40)
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldClearCache() = testDispatcherProvider.run {
        // Given
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-1", "state-2", Duration.ofMillis(123))

        registry.set(mapOf(definition1 to "My value 1", definition2 to "My value 2"))

        // When
        registry.clear()

        // The values cannot be read after removal.
        assertNull(registry.get(SharedStateDefinition("minion-1", "state-1")))
        assertNull(registry.get(SharedStateDefinition("minion-1", "state-2")))

    }

    @Test
    internal fun shouldClearCacheByMinionIds() = testDispatcherProvider.run {
        // Given
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-1", "state-2", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-2", "state-3", Duration.ofMillis(123))
        val definition4 = SharedStateDefinition("minion-3", "state-4", Duration.ofMillis(123))
        val definition5 = SharedStateDefinition("minion-4", "state-5", Duration.ofMillis(123))

        registry.set(
            mapOf(
                definition1 to "My value 1",
                definition2 to "My value 2",
                definition3 to "My value 3",
                definition4 to "My value 4",
                definition5 to "My value 5"
            ))

        // When
        registry.clear(listOf("minion-1", "minion-3"))

        // Then
        val allValues = registry.get(listOf(definition3, definition5))
        assertEquals("My value 3", allValues["state-3"])
        assertEquals("My value 5", allValues["state-5"])

        // The values cannot be read after removal.
        assertNull(registry.get(SharedStateDefinition("minion-1", "state-1")))
        assertNull(registry.get(SharedStateDefinition("minion-1", "state-2")))
        assertNull(registry.get(SharedStateDefinition("minion-3", "state-4")))

    }
}
