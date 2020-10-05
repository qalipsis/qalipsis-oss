package io.evolue.core.factories.sharedstates

import io.evolue.api.states.SharedStateDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class InMemorySharedStateRegistryTest {


    @Test
    internal fun shouldSetAndGet() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(123))

        registry[definition] = "My value"

        // The key should ignore the time to live of the definition.
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        // The value can be read twice.
        assertEquals("My value", registry[SharedStateDefinition("minion-1", "state")])
        assertEquals("My value", registry[SharedStateDefinition("minion-1", "state")])
    }

    @Test
    internal fun shouldSetAndRemove() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(123))

        registry[definition] = "My value"

        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        assertEquals("My value", registry.remove(SharedStateDefinition("minion-1", "state")))
        // The values cannot be read after removal.
        assertNull(registry[SharedStateDefinition("minion-1", "state")])
    }

    @Test
    internal fun shouldDeleteWhenSettingNull() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(123))

        registry[definition] = "My value"

        // The key should ignore the time to live of the definition.
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        assertEquals("My value", registry[SharedStateDefinition("minion-1", "state")])

        // The value is now set to null
        registry[definition] = null
        assertNull(registry[SharedStateDefinition("minion-1", "state")])
    }

    @Test
    internal fun shouldSetAllAndGetAll() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-1", "state-2", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-1", "state-3", Duration.ofMillis(123))

        registry.set(mapOf(definition1 to "My value 1", definition2 to "My value 2", definition3 to null))

        // The key should ignore the time to live of the definition.
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state-1")))
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state-2")))
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state-3")))
        assertEquals("My value 1", registry[SharedStateDefinition("minion-1", "state-1")])
        assertEquals("My value 2", registry[SharedStateDefinition("minion-1", "state-2")])
        assertNull(registry[SharedStateDefinition("minion-1", "state-3")])

        val allValues = registry.get(listOf(definition1, definition2, definition3))
        assertEquals("My value 1", allValues["state-1"])
        assertEquals("My value 2", allValues["state-2"])
        assertNull(allValues["state-3"])
    }

    @Test
    internal fun shouldSetAllAndRemoveAll() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-1", "state-2", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-1", "state-3", Duration.ofMillis(123))

        registry.set(mapOf(definition1 to "My value 1", definition2 to "My value 2", definition3 to null))

        // The key should ignore the time to live of the definition.
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state-1")))
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state-2")))
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state-3")))
        assertEquals("My value 1", registry[SharedStateDefinition("minion-1", "state-1")])
        assertEquals("My value 2", registry[SharedStateDefinition("minion-1", "state-2")])
        assertNull(registry[SharedStateDefinition("minion-1", "state-3")])

        val allValues = registry.remove(listOf(definition1, definition2, definition3))
        assertEquals("My value 1", allValues["state-1"])
        assertEquals("My value 2", allValues["state-2"])
        assertNull(allValues["state-3"])

        // The values cannot be read after removal.
        assertNull(registry[SharedStateDefinition("minion-1", "state-1")])
        assertNull(registry[SharedStateDefinition("minion-1", "state-2")])
    }

    @Test
    internal fun shouldIsolateSameStateForDifferentMinions() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state")
        val definition2 = SharedStateDefinition("minion-2", "state")

        registry[definition1] = "My value 1"
        registry[definition2] = "My value 2"

        assertEquals("My value 1", registry[definition1])
        assertEquals("My value 2", registry[definition2])
    }

    @Test
    internal fun shouldIsolateDifferentStatesForSameMinion() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1")
        val definition2 = SharedStateDefinition("minion-1", "state-2")

        registry[definition1] = "My value 1"
        registry[definition2] = "My value 2"

        assertEquals("My value 1", registry[definition1])
        assertEquals("My value 2", registry[definition2])
    }

    @Test
    internal fun shouldBeEvictedAfterDefaultTimeToLive() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state")

        registry[definition] = "My value"

        // Wait just before the time to live.
        Thread.sleep(40)
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(60)
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLive() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry[definition] = "My value"

        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait just before the time to live.
        Thread.sleep(60)
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(60)
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLiveAfterWrite() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry[definition] = "My value"

        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        // Wait just before the time to live.
        Thread.sleep(80)

        // The value is written again, the expiration should be updated.
        registry[SharedStateDefinition("minion-1", "state", Duration.ofMillis(200))] = "My value"

        // Wait just before the time to live.
        Thread.sleep(180)
        assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(40)
        assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLiveAfterRead() {
        val registry = InMemorySharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry[definition] = "My value"

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
}
