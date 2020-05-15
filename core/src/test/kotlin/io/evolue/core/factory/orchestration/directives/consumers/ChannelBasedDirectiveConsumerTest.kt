package io.evolue.core.factory.orchestration.directives.consumers

import io.evolue.core.cross.driving.TestDescriptiveDirective
import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import io.evolue.test.coroutines.AbstractCoroutinesTest
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class ChannelBasedDirectiveConsumerTest : AbstractCoroutinesTest() {

    @RelaxedMockK
    lateinit var processor1: DirectiveProcessor<*>

    @RelaxedMockK
    lateinit var processor2: DirectiveProcessor<*>

    @Test
    @Timeout(1)
    internal fun shouldConsumeAndProcessDirective() {
        // given
        val directive1 = TestDescriptiveDirective()
        val directive2 = TestDescriptiveDirective()
        every { processor1.accept(refEq(directive1)) } returns true
        every { processor2.accept(refEq(directive2)) } returns true
        val channel = Channel<Directive>(1)
        ChannelBasedDirectiveConsumer(channel, listOf(processor1, processor2))

        // when
        runBlocking {
            channel.send(directive1)
            channel.send(directive2)
            // Wait for the data to be consumed.
            delay(5)
        }

        // then
        coVerifyOrder {
            processor1.accept(refEq(directive1))
            processor2.accept(refEq(directive1))
            (processor1 as DirectiveProcessor<Directive>).process(refEq(directive1))

            processor1.accept(refEq(directive2))
            processor2.accept(refEq(directive2))
            (processor2 as DirectiveProcessor<Directive>).process(refEq(directive2))
        }
    }
}