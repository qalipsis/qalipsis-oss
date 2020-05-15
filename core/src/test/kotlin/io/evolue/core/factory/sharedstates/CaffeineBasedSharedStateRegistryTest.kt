package io.evolue.core.factory.sharedstates

import io.evolue.api.states.SharedStateDefinition
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class CaffeineBasedSharedStateRegistryTest {


    @Test
    internal fun shouldSetAndGet() {
        val registry = CaffeineBasedSharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(123))

        registry[definition] = "My value"

        // The key should ignore the time to live of the definition.
        Assertions.assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        Assertions.assertEquals("My value", registry[SharedStateDefinition("minion-1", "state")])
    }


    @Test
    internal fun shouldIsolateSameStateForDifferentMinions() {
        val registry = CaffeineBasedSharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state")
        val definition2 = SharedStateDefinition("minion-2", "state")

        registry[definition1] = "My value 1"
        registry[definition2] = "My value 2"

        Assertions.assertEquals("My value 1", registry[definition1])
        Assertions.assertEquals("My value 2", registry[definition2])
    }

    @Test
    internal fun shouldIsolateDifferentStatesForSameMinion() {
        val registry = CaffeineBasedSharedStateRegistry(Duration.ofMillis(50))
        val definition1 = SharedStateDefinition("minion-1", "state-1")
        val definition2 = SharedStateDefinition("minion-1", "state-2")

        registry[definition1] = "My value 1"
        registry[definition2] = "My value 2"

        Assertions.assertEquals("My value 1", registry[definition1])
        Assertions.assertEquals("My value 2", registry[definition2])
    }

    @Test
    internal fun shouldBeEvictedAfterDefaultTimeToLive() {
        val registry = CaffeineBasedSharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state")

        registry[definition] = "My value"

        // Wait just before the time to live.
        Thread.sleep(40)
        Assertions.assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(60)
        Assertions.assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLive() {
        val registry = CaffeineBasedSharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry[definition] = "My value"

        Assertions.assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait just before the time to live.
        Thread.sleep(60)
        Assertions.assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(60)
        Assertions.assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLiveAfterWrite() {
        val registry = CaffeineBasedSharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry[definition] = "My value"

        Assertions.assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        // Wait just before the time to live.
        Thread.sleep(80)

        // The value is written again, the expiration should be updated.
        registry[SharedStateDefinition("minion-1", "state", Duration.ofMillis(200))] = "My value"

        // Wait just before the time to live.
        Thread.sleep(180)
        Assertions.assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(40)
        Assertions.assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }

    @Test
    internal fun shouldBeEvictedAfterSpecifiedTimeToLiveAfterRead() {
        val registry = CaffeineBasedSharedStateRegistry(Duration.ofMillis(50))
        val definition = SharedStateDefinition("minion-1", "state", Duration.ofMillis(100))

        registry[definition] = "My value"

        Assertions.assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))
        // Wait just before the time to live.
        Thread.sleep(80)

        // The value is read, the expiration should be updated.
        val value: String? = registry[definition]

        // Wait just before the time to live.
        Thread.sleep(80)
        Assertions.assertTrue(registry.contains(SharedStateDefinition("minion-1", "state")))

        // Wait after the time to live.
        Thread.sleep(40)
        Assertions.assertFalse(registry.contains(SharedStateDefinition("minion-1", "state")))
    }
}