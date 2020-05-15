package io.evolue.core.factory.steps.correlation

import io.evolue.api.context.CorrelationRecord
import io.evolue.api.messaging.TopicMode
import io.evolue.api.messaging.topic
import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.core.exceptions.NotInitializedStepException
import io.evolue.core.factory.steps.StepTestHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Duration

internal class CorrelationStepTest {

    @Test
    @Timeout(1)
    internal fun shouldNotExecuteWhenNotInitialized() {
        val remoteChannel = Channel<Long>(100)
        val topic = topic(TopicMode.UNICAST, fromBeginning = true)
        val step = CorrelationStep<Long, Long>("corr", emptyList(), Duration.ofSeconds(10),
            Duration.ofSeconds(10)) { record -> 1 }
        val ctx = StepTestHelper.createStepContext<Long, Long>()

        // then
        assertThrows<NotInitializedStepException> {
            runBlocking {
                step.execute(ctx)
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun shouldSuspendUntilAllValuesAreReceived() {
        // given
        val channel1 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary1>>(1)
        val channel2 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary2>>(1)
        val step = CorrelationStep<TestCorrelationEntityFromPrimary, Array<Any?>>("corr",
            listOf(SecondaryCorrelation("secondary-1",
                channel1) { record -> (record.value as TestCorrelationEntityFromSecondary1).key },
                SecondaryCorrelation("secondary-2",
                    channel2) { record -> (record.value as TestCorrelationEntityFromSecondary2).key }
            ),
            Duration.ofSeconds(10),
            Duration.ofSeconds(10)
        ) { record -> (record.value as TestCorrelationEntityFromPrimary).key }
        runBlocking { step.init() }
        val entityFromPrimary = TestCorrelationEntityFromPrimary(123, "From Primary")
        val entityFromFromSecondary1 = TestCorrelationEntityFromSecondary1(123, "From Secondary 1")
        val entityFromFromSecondary2 = TestCorrelationEntityFromSecondary2(123, "From Secondary 2")

        val ctx = StepTestHelper.createStepContext<TestCorrelationEntityFromPrimary, Array<Any?>>(entityFromPrimary)

        // when
        val execution = GlobalScope.launch {
            step.execute(ctx)
        }
        // Send with delay and in an order different from secondary correlations definitions.
        runBlocking {
            delay(50)
            channel2.send(CorrelationRecord(ctx.minionId, "secondary-2",
                entityFromFromSecondary2))
            delay(50)
            channel1.send(CorrelationRecord(ctx.minionId, "secondary-1",
                entityFromFromSecondary1))

            // Wait for the execution to complete.
            execution.join()
        }

        // then
        Assertions.assertFalse(ctx.exhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = runBlocking {
            (ctx.output as Channel).receive()
        }
        Assertions.assertArrayEquals(arrayOf(entityFromPrimary, entityFromFromSecondary1, entityFromFromSecondary2),
            output)
        Assertions.assertFalse(step.hasKeyInCache(123))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotSuspendWhenAllValuesAreAlreadyReceived() {
        // given
        val channel1 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary1>>(1)
        val channel2 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary2>>(1)
        val step = CorrelationStep<TestCorrelationEntityFromPrimary, Array<Any?>>("corr",
            listOf(SecondaryCorrelation("secondary-1",
                channel1) { record -> (record.value as TestCorrelationEntityFromSecondary1).key },
                SecondaryCorrelation("secondary-2",
                    channel2) { record -> (record.value as TestCorrelationEntityFromSecondary2).key }
            ),
            Duration.ofSeconds(10),
            Duration.ofSeconds(10)
        ) { record -> (record.value as TestCorrelationEntityFromPrimary).key }
        runBlocking { step.init() }
        val entityFromPrimary = TestCorrelationEntityFromPrimary(123, "From Primary")
        val entityFromFromSecondary1 = TestCorrelationEntityFromSecondary1(123, "From Secondary 1")
        val entityFromFromSecondary2 = TestCorrelationEntityFromSecondary2(123, "From Secondary 2")

        val ctx = StepTestHelper.createStepContext<TestCorrelationEntityFromPrimary, Array<Any?>>(entityFromPrimary)

        // when
        // Send with in an order different from the secondary correlations definitions.
        runBlocking {
            channel2.send(CorrelationRecord(ctx.minionId, "secondary-2",
                entityFromFromSecondary2))
            channel1.send(CorrelationRecord(ctx.minionId, "secondary-1",
                entityFromFromSecondary1))
        }
        runBlocking {
            step.execute(ctx)
        }

        // then
        Assertions.assertFalse(ctx.exhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = runBlocking {
            (ctx.output as Channel).receive()
        }
        Assertions.assertArrayEquals(arrayOf(entityFromPrimary, entityFromFromSecondary1, entityFromFromSecondary2),
            output)
        Assertions.assertFalse(step.hasKeyInCache(123))
    }

    /**
     * This test validates the concurrency behavior when running the unit tests massively.
     */
    @Timeout(1)
    @RepeatedTest(100)
    internal fun shouldSucceedWhenSendingMessagesConcurrentlyBeforeTheExecution() {
        // given
        val channel1 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary1>>(1)
        val channel2 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary2>>(1)
        val step = CorrelationStep<TestCorrelationEntityFromPrimary, Array<Any?>>("corr",
            listOf(SecondaryCorrelation("secondary-1",
                channel1) { record -> (record.value as TestCorrelationEntityFromSecondary1).key },
                SecondaryCorrelation("secondary-2",
                    channel2) { record -> (record.value as TestCorrelationEntityFromSecondary2).key }
            ),
            Duration.ofSeconds(10),
            Duration.ofSeconds(10)
        ) { record -> (record.value as TestCorrelationEntityFromPrimary).key }
        runBlocking { step.init() }
        val entityFromPrimary = TestCorrelationEntityFromPrimary(123, "From Primary")
        val entityFromFromSecondary1 = TestCorrelationEntityFromSecondary1(123, "From Secondary 1")
        val entityFromFromSecondary2 = TestCorrelationEntityFromSecondary2(123, "From Secondary 2")

        val ctx = StepTestHelper.createStepContext<TestCorrelationEntityFromPrimary, Array<Any?>>(entityFromPrimary)

        // when sending two messages concurrently
        val startLatch = SuspendedCountLatch(1)
        GlobalScope.launch {
            startLatch.await()
            channel1.send(CorrelationRecord(ctx.minionId, "secondary-1",
                entityFromFromSecondary1))
        }
        GlobalScope.launch {
            startLatch.await()
            channel2.send(CorrelationRecord(ctx.minionId, "secondary-2",
                entityFromFromSecondary2))
        }
        runBlocking {
            startLatch.release()
            step.execute(ctx)
        }

        // then
        Assertions.assertFalse(ctx.exhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = runBlocking {
            (ctx.output as Channel).receive()
        }
        Assertions.assertArrayEquals(arrayOf(entityFromPrimary, entityFromFromSecondary1, entityFromFromSecondary2),
            output)
        Assertions.assertFalse(step.hasKeyInCache(123))
    }

    @Test
    @Timeout(1)
    internal fun shouldSuspendUntilTimeout() {
        // given
        val channel1 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary1>>(1)
        val channel2 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary2>>(1)
        val step = CorrelationStep<TestCorrelationEntityFromPrimary, Array<Any?>>("corr",
            listOf(SecondaryCorrelation("secondary-1",
                channel1) { record -> (record.value as TestCorrelationEntityFromSecondary1).key },
                SecondaryCorrelation("secondary-2",
                    channel2) { record -> (record.value as TestCorrelationEntityFromSecondary2).key }
            ),
            Duration.ofMillis(10),
            Duration.ofSeconds(10)
        ) { record -> (record.value as TestCorrelationEntityFromPrimary).key }
        runBlocking { step.init() }
        val entityFromPrimary = TestCorrelationEntityFromPrimary(123, "From Primary")
        val ctx = StepTestHelper.createStepContext<TestCorrelationEntityFromPrimary, Array<Any?>>(entityFromPrimary)

        // when
        assertThrows<TimeoutCancellationException> {
            runBlocking {
                step.execute(ctx)
            }
        }

        // then
        Assertions.assertFalse(ctx.exhausted)
        (ctx.output as Channel).apply {
            Assertions.assertFalse(isClosedForReceive)
            Assertions.assertTrue(isEmpty)
        }
        Assertions.assertFalse(step.hasKeyInCache(123))
    }

    @Test
    @Timeout(1)
    internal fun shouldEvictCacheAfterTimeout() {
        // given
        val channel1 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary1>>(1)
        val channel2 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary2>>(1)
        val step = CorrelationStep<TestCorrelationEntityFromPrimary, Array<Any?>>("corr",
            listOf(SecondaryCorrelation("secondary-1",
                channel1) { record -> (record.value as TestCorrelationEntityFromSecondary1).key },
                SecondaryCorrelation("secondary-2",
                    channel2) { record -> (record.value as TestCorrelationEntityFromSecondary2).key }
            ),
            Duration.ofMillis(1),
            Duration.ofMillis(90)
        ) { record -> (record.value as TestCorrelationEntityFromPrimary).key }
        val entityFromFromSecondary1 = TestCorrelationEntityFromSecondary1(123, "From Secondary 1")
        val entityFromFromSecondary2 = TestCorrelationEntityFromSecondary2(123, "From Secondary 2")

        // when
        runBlocking {
            step.init()
            channel2.send(CorrelationRecord("any", "secondary-2",
                entityFromFromSecondary2))
            channel1.send(CorrelationRecord("any", "secondary-1",
                entityFromFromSecondary1))
            // Wait for the data to be consumed.
            delay(20)
        }

        // then
        Assertions.assertTrue(step.hasKeyInCache(123))

        // when
        // Wait after the cache timeout.
        runBlocking {
            delay(100)
        }

        // then
        Assertions.assertFalse(step.hasKeyInCache(123))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotCacheNullKey() {
        // given
        val channel1 = Channel<CorrelationRecord<TestCorrelationEntityFromSecondary1>>(1)
        val step = CorrelationStep<TestCorrelationEntityFromPrimary, Array<Any?>>("corr",
            listOf(SecondaryCorrelation("secondary-1",
                // The conversion from record to key always returns null.
                channel1) { record -> null }
            ),
            Duration.ofMillis(1),
            Duration.ofMillis(90)
        ) { record -> (record.value as TestCorrelationEntityFromPrimary).key }
        val entityFromFromSecondary1 = TestCorrelationEntityFromSecondary1(123, "From Secondary 1")

        // when
        runBlocking {
            step.init()
            channel1.send(CorrelationRecord("any", "secondary-1",
                entityFromFromSecondary1))
            // Wait for the data to be consumed.
            delay(20)
        }

        // then
        Assertions.assertTrue(step.isCacheEmpty())
    }

    @Test
    @Timeout(1)
    internal fun shouldNotExecuteNullKey() {
        // given
        // The conversion from record to key always returns null.
        val step =
            CorrelationStep<TestCorrelationEntityFromPrimary, Array<Any?>>("corr", emptyList(), Duration.ofSeconds(10),
                Duration.ofSeconds(10)) { record -> null }
        val entityFromPrimary = TestCorrelationEntityFromPrimary(123, "From Primary")
        val ctx = StepTestHelper.createStepContext<TestCorrelationEntityFromPrimary, Array<Any?>>(entityFromPrimary)
        runBlocking {
            step.init()
        }

        // when
        runBlocking {
            step.execute(ctx)
        }

        // then
        Assertions.assertFalse(ctx.exhausted)
        (ctx.output as Channel).apply {
            Assertions.assertFalse(isClosedForReceive)
            Assertions.assertTrue(isEmpty)
        }
        Assertions.assertTrue(step.isCacheEmpty())
    }

    private abstract class TestCorrelationEntity(val key: Int, val value: Any)
    private class TestCorrelationEntityFromPrimary(key: Int, value: Any) : TestCorrelationEntity(key, value)
    private class TestCorrelationEntityFromSecondary1(key: Int, value: Any) : TestCorrelationEntity(key, value)
    private class TestCorrelationEntityFromSecondary2(key: Int, value: Any) : TestCorrelationEntity(key, value)
}