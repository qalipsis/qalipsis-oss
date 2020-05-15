package io.evolue.core.cross.driving.directives.inmemory

import io.evolue.core.cross.driving.TestDescriptiveDirective
import io.evolue.core.cross.driving.TestListDirective
import io.evolue.core.cross.driving.TestQueueDirective
import io.evolue.core.cross.driving.TestSingleUseDirective
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.test.mockk.coVerifyOnce
import io.mockk.confirmVerified
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
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
