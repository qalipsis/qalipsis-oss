package io.qalipsis.core.head.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.directives.TestListDirective
import io.qalipsis.core.directives.TestListDirectiveReference
import io.qalipsis.core.directives.TestQueueDirective
import io.qalipsis.core.directives.TestQueueDirectiveReference
import io.qalipsis.core.directives.TestSingleUseDirective
import io.qalipsis.core.directives.TestSingleUseDirectiveReference
import io.qalipsis.core.feedbacks.DirectiveFeedback
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.inmemory.InMemoryDirectiveRegistry
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Collections

/**
 * @author Eric Jessé
 */
@WithMockk
internal class InMemoryDirectiveRegistryTest {

    @RelaxedMockK
    lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @Test
    @Timeout(1)
    internal fun saveAndPopQueueDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackFactoryChannel)
        val directive = TestQueueDirective((0 until 20).toList())
        val capturedDirectiveFeedBacks = mutableListOf<DirectiveFeedback>()
        coEvery { feedbackFactoryChannel.publish(capture(capturedDirectiveFeedBacks)) } returns Unit

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.pop(TestQueueDirectiveReference("other-key"))
        assertNull(notExistingDirective)

        // then
        repeat(20) { index ->
            assertEquals(index, registry.pop(directive.toReference()))
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
        assertNull(emptyQueue)

    }

    @Test
    @Timeout(5)
    internal fun massiveConcurrentPopQueueDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackFactoryChannel)
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
        assertEquals(20000, retrievedValues.size)

        // then
        val emptyQueue = registry.pop(directive.toReference())
        assertNull(emptyQueue)
    }

    @Test
    @Timeout(1)
    internal fun saveAndReadListDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackFactoryChannel)
        val directive = TestListDirective((0 until 20).toList())

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.list(TestListDirectiveReference("other-key"))
        assertEquals(emptyList<Int>(), notExistingDirective)

        // then all the calls return the same set.
        repeat(20) {
            assertEquals((0 until 20).toList(), registry.list(directive.toReference()))
        }
    }

    @Test
    @Timeout(1)
    internal fun saveAndReadSingleUseDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackFactoryChannel)
        val directive = TestSingleUseDirective(100)

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.read(TestSingleUseDirectiveReference("other-key"))
        assertNull(notExistingDirective)

        // then
        val existingValue = registry.read(directive.toReference())
        assertEquals(100, existingValue)

        // then
        val emptyValue = registry.read(directive.toReference())
        assertNull(emptyValue)
    }

    @Test
    @Timeout(10)
    internal fun saveThenGetAndDeleteStandardDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackFactoryChannel)
        val directive = TestDescriptiveDirective("this-is-the-key")

        // when
        registry.keep(directive)

        // then
        val retrieved = registry.get("this-is-the-key")
        assertEquals(directive, retrieved)

        // then
        val notExistingDirective = registry.get("other-key")
        assertNull(notExistingDirective)

        // then
        registry.remove("this-is-the-key")
        val deletedValue = registry.get("this-is-the-key")
        assertNull(deletedValue)
    }
}