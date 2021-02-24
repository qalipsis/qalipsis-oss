package io.qalipsis.core.factories.orchestration.directives.processors.minions

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.core.cross.directives.MinionsCreationDirectiveReference
import io.qalipsis.core.cross.directives.TestDescriptiveDirective
import io.qalipsis.core.factories.orchestration.ScenariosRegistry
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@ExperimentalCoroutinesApi
@WithMockk
internal class MinionsCreationDirectiveProcessorTest {

    @RelaxedMockK
    lateinit var registry: DirectiveRegistry

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var scenariosRegistry: ScenariosRegistry

    @InjectMockKs
    lateinit var processor: MinionsCreationDirectiveProcessor

    @Test
    @Timeout(1)
    internal fun shouldAcceptMinionsCreationDirective() {
        val directive =
            MinionsCreationDirectiveReference(
                "my-directive",
                "my-campaign", "my-scenario", "my-dag"
            )
        every { scenariosRegistry["my-scenario"]?.contains("my-dag") } returns true

        Assertions.assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptNotMinionsCreationDirective() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptMinionsCreationDirectiveForUnknownDag() {
        val directive =
            MinionsCreationDirectiveReference(
                "my-directive",
                "my-campaign", "my-scenario", "my-dag"
            )
        every { scenariosRegistry["my-scenario"]?.contains("my-dag") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldPopNextMinionToCreateFromQueue() = runBlockingTest {
        // given
        val directive =
            MinionsCreationDirectiveReference(
                "my-directive",
                "my-campaign", "my-scenario", "my-dag"
            )
        val counterCall = AtomicInteger(10)
        coEvery {
            registry.pop(refEq(directive))
        } answers {
            if (counterCall.getAndDecrement() > 0) "my-minion" else null
        }

        // when
            processor.process(directive)
            // Wait for the directive to be fully processed.
            delay(20)

        // then
        coVerifyExactly(11) {
            registry.pop(directive)
        }
        coVerifyExactly(10) {
            minionsKeeper.create("my-campaign", "my-scenario", "my-dag", "my-minion")
        }
        confirmVerified(registry, minionsKeeper)
    }

    @Test
    @Timeout(1)
    internal fun shouldPopNextMinionButDoNothingWhereThereIsNone() = runBlockingTest {
        // given
        val directive =
            MinionsCreationDirectiveReference(
                "my-directive",
                "my-campaign", "my-scenario", "my-dag"
            )
        coEvery {
            registry.pop(refEq(directive))
        } returns null

        // when
            processor.process(directive)
            // Wait for the directive to be fully completed.
            delay(20)

        // then
        coVerify {
            registry.pop(directive)
        }
        confirmVerified(registry, minionsKeeper)
    }
}
