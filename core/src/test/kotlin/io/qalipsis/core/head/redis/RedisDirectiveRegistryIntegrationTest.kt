package io.qalipsis.core.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.serialization.Serializable
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.TestListDirective
import io.qalipsis.core.directives.TestListDirectiveReference
import io.qalipsis.core.directives.TestQueueDirective
import io.qalipsis.core.directives.TestQueueDirectiveReference
import io.qalipsis.core.directives.TestSingleUseDirective
import io.qalipsis.core.directives.TestSingleUseDirectiveReference
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Collections

/**
 * @author Gabriel Moraes
 */
@WithMockk
@ExperimentalLettuceCoroutinesApi
@Serializable([TestQueueDirective::class, TestListDirective::class, TestSingleUseDirective::class])
internal class RedisDirectiveRegistryIntegrationTest: AbstractRedisIntegrationTest(){

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    private val feedbackFactoryChannel = mockk<FeedbackFactoryChannel>(relaxed = true)

    @MockBean(FeedbackFactoryChannel::class)
    fun feedbackFactoryChannel() = feedbackFactoryChannel

    @Inject
    private lateinit var registry: RedisDirectiveRegistry

    @Test
    @Timeout(10)
    internal fun saveAndPopQueueDirective() = testDispatcherProvider.run {

        // given
        val directive = TestQueueDirective((0 until 20).toList())
        val capturedDirectiveFeedBacks = mutableListOf<DirectiveFeedback>()
        coEvery { feedbackFactoryChannel.publish(capture(capturedDirectiveFeedBacks)) } returns Unit

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.pop(TestQueueDirectiveReference("other-key"))
        Assertions.assertNull(notExistingDirective)

        // then
        repeat(20) { index ->
            Assertions.assertEquals(index, registry.pop(directive.toReference()))
        }

        // then
        coVerify { feedbackFactoryChannel.publish(any()) }
        assertThat(capturedDirectiveFeedBacks[0]).all {
            prop(DirectiveFeedback::key).isNotNull()
            prop(DirectiveFeedback::directiveKey).isEqualTo(directive.toReference().key)
            prop(DirectiveFeedback::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
            prop(DirectiveFeedback::error).isNull()
        }
        assertThat(capturedDirectiveFeedBacks[1]).all {
            prop(DirectiveFeedback::key).isNotNull()
            prop(DirectiveFeedback::directiveKey).isEqualTo(directive.toReference().key)
            prop(DirectiveFeedback::status).isEqualTo(FeedbackStatus.COMPLETED)
            prop(DirectiveFeedback::error).isNull()
        }
        val emptyQueue = registry.pop(directive.toReference())
        Assertions.assertNull(emptyQueue)
    }

    @Test
    @Timeout(20)
    internal fun massiveConcurrentPopQueueDirective() = testDispatcherProvider.run {
        // given
        val directive = TestQueueDirective((0 until 20000).toList())
        val startLatch = SuspendedCountLatch(1)
        val retrievedValues = Collections.synchronizedSet(mutableSetOf<Int>())
        val countDownLatch = SuspendedCountLatch(200)

        registry.save(directive.toReference().key, directive)

        for (i in 0 until 200) {
            launch {
                // Block the coroutines until the flag is closed.
                startLatch.await()
                repeat(100) {
                    val value = registry.pop(directive.toReference())
                    retrievedValues.add(value)
                }
                countDownLatch.decrement()
            }
        }

        // when
        startLatch.release()
        countDownLatch.await()

        // then all the unique values are listed.
        Assertions.assertEquals(20000, retrievedValues.size)

        // then
        val emptyQueue = registry.pop(directive.toReference())
        Assertions.assertNull(emptyQueue)
    }

    @Test
    @Timeout(10)
    internal fun saveAndReadListDirective() = testDispatcherProvider.run {
        // given
        val directive = TestListDirective((0 until 20).toList())

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.list(TestListDirectiveReference("other-key"))
        Assertions.assertEquals(emptyList<Int>(), notExistingDirective)

        // then all the calls return the same set.
        repeat(20) {
            Assertions.assertEquals((0 until 20).toList(), registry.list(directive.toReference()))
        }
    }

    @Test
    @Timeout(10)
    internal fun saveAndReadSingleUseDirective() = testDispatcherProvider.run {
        // given
        val directive = TestSingleUseDirective(100)

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.read(TestSingleUseDirectiveReference("other-key"))
        Assertions.assertNull(notExistingDirective)

        // then
        val existingValue = registry.read(directive.toReference())
        Assertions.assertEquals(100, existingValue)

        // then
        val emptyValue = registry.read(directive.toReference())
        Assertions.assertNull(emptyValue)
    }
}