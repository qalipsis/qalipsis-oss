package io.qalipsis.core.cross.directives.inmemory

import io.mockk.confirmVerified
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.core.cross.directives.TestDescriptiveDirective
import io.qalipsis.core.cross.directives.TestListDirective
import io.qalipsis.core.cross.directives.TestQueueDirective
import io.qalipsis.core.cross.directives.TestSingleUseDirective
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@WithMockk
internal class InMemoryDirectiveProducerTest {

    @RelaxedMockK
    lateinit var registry: DirectiveRegistry

    @InjectMockKs
    lateinit var producer: InMemoryDirectiveProducer

    @Test
    internal fun shouldSaveAndProduceQueueDirective() {
        // given
        val directive = TestQueueDirective((0 until 20).toList())

        // when
        runBlocking {
            producer.publish(directive)
        }

        // then
        coVerifyOnce {
            registry.save("my-queue-directive", directive)
        }
        val published = runBlocking {
            producer.channel.receive()
        }
        Assertions.assertSame(directive.toReference(), published)
    }

    @Test
    internal fun shouldSaveAndProduceListDirective() {
        // given
        val directive = TestListDirective((0 until 20).toList())

        // when
        runBlocking {
            producer.publish(directive)
        }

        // then
        coVerifyOnce {
            registry.save("my-list-directive", directive)
        }
        val published = runBlocking {
            producer.channel.receive()
        }
        Assertions.assertSame(directive.toReference(), published)
    }

    @Test
    internal fun shouldSaveAndSingleUseListDirective() {
        // given
        val directive = TestSingleUseDirective(100)

        // when
        runBlocking {
            producer.publish(directive)
        }

        // then
        coVerifyOnce {
            registry.save("my-single-use-directive", directive)
        }
        val published = runBlocking {
            producer.channel.receive()
        }
        Assertions.assertSame(directive.toReference(), published)
    }

    @Test
    internal fun shouldSaveAndProduceOtherDirective() {
        // given
        val directive = TestDescriptiveDirective()

        // when
        runBlocking {
            producer.publish(directive)
        }

        // then
        confirmVerified(registry)
        val published = runBlocking {
            producer.channel.receive()
        }
        Assertions.assertSame(directive, published)
    }
}
