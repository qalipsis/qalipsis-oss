package io.qalipsis.core.factory.redis

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import java.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Svetlana Paliashchuk
 */
@WithMockk
@ExperimentalLettuceCoroutinesApi
internal class RedisSharedStateRegistryIntegrationTest: AbstractRedisIntegrationTest() {

    @Inject
    private lateinit var redisCoroutinesCommands: RedisCoroutinesCommands<String, String>

    @Inject
    private lateinit var serializer: DistributionSerializer

    @Inject
    private lateinit var registry: RedisSharedStateRegistry

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @BeforeEach
    fun setup() = testDispatcherProvider.run {
        val definition1 = SharedStateDefinition("minion-1", "state1", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-2", "state2", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-3", "state3", Duration.ofMillis(123))

        val payload1 = Person("Ann", 22)
        val payload2 = Person("Mike", 20)
        val payload3 = Person("Paul", 25)

        redisCoroutinesCommands.set("$KEY_PREFIX:${definition1.minionId}:${definition1.sharedStateName}", serializer.serialize(payload1).decodeToString())
        redisCoroutinesCommands.set("$KEY_PREFIX:${definition2.minionId}:${definition2.sharedStateName}", serializer.serialize(payload2).decodeToString())
        redisCoroutinesCommands.set("$KEY_PREFIX:${definition3.minionId}:${definition3.sharedStateName}", serializer.serialize(payload3).decodeToString())

    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        redisCoroutinesCommands.flushall()
    }

    @Test
    @Timeout(10)
    fun shouldContainRecordInCache() = testDispatcherProvider.run {
        //given
        val definition = SharedStateDefinition("minion-1", "state1", Duration.ofMillis(123))

        //when
        val result = registry.contains(definition)

        //then
        Assertions.assertTrue(result)
    }

    @Test
    @Timeout(10)
    fun shouldNotContainRecordInCache() = testDispatcherProvider.run {
        //given
        //this definition does not exist in cache
        val definition = SharedStateDefinition("minion-3", "state33", Duration.ofMillis(123))

        //when
        val result = registry.contains(definition)

        //then
        Assertions.assertFalse(result)
    }

    @Test
    @Timeout(10)
    internal fun shouldGetTheRecordFromCache() = testDispatcherProvider.run {
        //given
        val definition = SharedStateDefinition("minion-2", "state2", Duration.ofMillis(123))

        //when
        val result = registry.get<Person>(definition)

        //then
        assertThat {
            result?.name.equals("Mike")
            result?.age == (20)
        }
    }

    @Test
    @Timeout(10)
    fun shouldNotGetTheRecordFromCache() = testDispatcherProvider.run {
        //given
        //this definition does not exist in cache
        val definition = SharedStateDefinition("minion-3", "state33", Duration.ofMillis(123))

        //when
        val result = registry.get<Person>(definition)

        //then
        Assertions.assertNull(result)
    }

    @Test
    @Timeout(10)
    fun shouldGetSeveralRecordsFromCache() = testDispatcherProvider.run {
        //given
        val definition1 = SharedStateDefinition("minion-1", "state1", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-3", "state3", Duration.ofMillis(123))
        val payload1 = Person("Ann", 22)
        val payload3 = Person("Paul", 25)

        //when
        val result = registry.get(listOf(definition1, definition3))

        //then
        assertThat(result).hasSize(2)
        assertThat(result).containsAll(
            Pair("$KEY_PREFIX:${definition1.minionId}:${definition1.sharedStateName}", payload1),
            Pair("$KEY_PREFIX:${definition3.minionId}:${definition3.sharedStateName}", payload3)
        )
    }

    @Test
    @Timeout(10)
    fun shouldGetSeveralRecordsFromCacheIfOneIsNotSaved() = testDispatcherProvider.run {
        //given
        val nonExistingDefinition = SharedStateDefinition("minion-3", "state33", Duration.ofMillis(123))
        val definition1 = SharedStateDefinition("minion-1", "state1", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-3", "state3", Duration.ofMillis(123))
        val payload1 = Person("Ann", 22)
        val payload3 = Person("Paul", 25)

        //when
        val result = registry.get(listOf(nonExistingDefinition, definition1, definition3))

        //then
        assertThat(result).hasSize(3)
        assertThat(result).containsAll(
                Pair("$KEY_PREFIX:${nonExistingDefinition.minionId}:${nonExistingDefinition.sharedStateName}", null),
                Pair("$KEY_PREFIX:${definition1.minionId}:${definition1.sharedStateName}", payload1),
                Pair("$KEY_PREFIX:${definition3.minionId}:${definition3.sharedStateName}", payload3)
        )
    }

    @Test
    @Timeout(10)
    fun shouldNotGetSeveralRecordsFromCache() = testDispatcherProvider.run {
        //given
        val definition1 = SharedStateDefinition("minion-3", "state33", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-4", "state5", Duration.ofMillis(123))

        //when
        val result = registry.get(listOf(definition1, definition2))

        //then
        assertThat(result).hasSize(2)
        assertThat(result).containsAll(
                Pair("$KEY_PREFIX:${definition1.minionId}:${definition1.sharedStateName}", null),
                Pair("$KEY_PREFIX:${definition2.minionId}:${definition2.sharedStateName}", null)
        )
    }

    @Test
    @Timeout(10)
    fun shouldRemoveTheRecordFromCache() = testDispatcherProvider.run {
        //given
        val definition = SharedStateDefinition("minion-1", "state1", Duration.ofMillis(123))

        //when
        val result = registry.remove<Person>(definition)

        //then
        assertThat {
            result?.name.equals("Ann")
            result?.age == (22)
        }
    }

    @Test
    @Timeout(10)
    fun shouldNotRemoveTheRecordFromCache() = testDispatcherProvider.run {
        //given

        val definition = SharedStateDefinition("minion-3", "state33", Duration.ofMillis(123))

        //when
        val result = registry.remove<Person>(definition)

        //then
        Assertions.assertNull(result)
    }

    @Test
    @Timeout(10)
    fun shouldRemoveSeveralRecordsFromCache() = testDispatcherProvider.run {
        //given
        val definition1 = SharedStateDefinition("minion-1", "state1", Duration.ofMillis(123))
        val definition3 = SharedStateDefinition("minion-3", "state3", Duration.ofMillis(123))

        val payload1 = Person("Ann", 22)
        val payload3 = Person("Paul", 25)

        //when
        val result = registry.remove(listOf(definition1, definition3))

        //then
        assertThat(result).hasSize(2)
        assertThat(result).containsAll(
            Pair("$KEY_PREFIX:${definition1.minionId}:${definition1.sharedStateName}", payload1),
            Pair("$KEY_PREFIX:${definition3.minionId}:${definition3.sharedStateName}", payload3)
        )
    }

    @Test
    @Timeout(10)
    fun shouldNotRemoveSeveralRecordsFromCacheIfTheyDoNotExist() = testDispatcherProvider.run {
        //given
        val definition1 = SharedStateDefinition("minion-3", "state33", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-4", "state5", Duration.ofMillis(123))

        //when
        val result = registry.remove(listOf(definition1, definition2))

        //then
        assertThat(result).hasSize(2)
        assertThat(result).containsAll(
                Pair("$KEY_PREFIX:${definition1.minionId}:${definition1.sharedStateName}", null),
                Pair("$KEY_PREFIX:${definition2.minionId}:${definition2.sharedStateName}", null)
        )
    }

    @Test
    @Timeout(10)
    fun shouldSetTheRecordInCache() = testDispatcherProvider.run {
        //given
        val definition = SharedStateDefinition("minion-100", "state100", Duration.ofMillis(123))
        val payload = Person("Nick", 30)

        //when
        registry.set(definition, payload)

        //then
        val record = redisCoroutinesCommands.get("$KEY_PREFIX:${definition.minionId}:${definition.sharedStateName}")?.let {
            serializer.deserialize<Person>(it.encodeToByteArray())
        }
        assertThat {
            record?.name.equals("Nick")
            record?.age == (30)
        }
    }

    @Test
    @Timeout(10)
    fun shouldNotSetTheRecordInCacheIfPayloadIsNull() = testDispatcherProvider.run {
        //given
        val definition = SharedStateDefinition("minion-100", "state100", Duration.ofMillis(123))
        val payload = null

        //when
        registry.set(definition, payload)

        //then
        val result = registry.contains(definition)
        Assertions.assertFalse(result)
    }

    @Test
    @Timeout(10)
    fun shouldSetSeveralRecordsInCache() = testDispatcherProvider.run {
        //given
        val definition1 = SharedStateDefinition("minion-100", "state100", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-200", "state200", Duration.ofMillis(123))

        val payload1 = Person("Nick", 30)
        val payload2 = Person("Peter", 25)

        //when
        registry.set(mapOf(definition1 to payload1, definition2 to payload2))

        //then
        val record1 = redisCoroutinesCommands.get("$KEY_PREFIX:${definition1.minionId}:${definition1.sharedStateName}")?.let {
            serializer.deserialize<Person>(it.encodeToByteArray())
        }
        val record2 = redisCoroutinesCommands.get("$KEY_PREFIX:${definition2.minionId}:${definition2.sharedStateName}")?.let {
            serializer.deserialize<Person>(it.encodeToByteArray())
        }

        assertThat {
            record1?.name.equals("Nick")
            record1?.age == (30)
        }
        assertThat{
            record2?.name.equals("Peter")
            record2?.age == (25)
        }
    }

    @Test
    @Timeout(10)
    fun shouldNotSetSeveralRecordsInCacheIfPayloadsAreNull() = testDispatcherProvider.run {
        //given
        val definition1 = SharedStateDefinition("minion-100", "state100", Duration.ofMillis(123))
        val definition2 = SharedStateDefinition("minion-200", "state200", Duration.ofMillis(123))

        val payload1 = null
        val payload2 = null

        //when
        registry.set(mapOf(definition1 to payload1, definition2 to payload2))

        //then
        val result1 = registry.contains(definition1)
        Assertions.assertFalse(result1)

        val result2 = registry.contains(definition2)
        Assertions.assertFalse(result2)
    }

    companion object{
        val KEY_PREFIX = "shared-state-registry"
    }

}

data class Person(val name: String, val age: Int)
