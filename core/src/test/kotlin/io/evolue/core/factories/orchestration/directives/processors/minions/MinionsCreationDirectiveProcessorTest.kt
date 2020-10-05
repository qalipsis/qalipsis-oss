package io.evolue.core.factories.orchestration.directives.processors.minions

import io.evolue.api.context.MinionId
import io.evolue.api.orchestration.directives.DirectiveRegistry
import io.evolue.core.cross.directives.MinionsCreationDirectiveReference
import io.evolue.core.cross.directives.TestDescriptiveDirective
import io.evolue.core.factories.orchestration.MinionsKeeper
import io.evolue.core.factories.orchestration.ScenariosKeeper
import io.evolue.test.coroutines.CleanCoroutines
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.coVerifyExactly
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@WithMockk
@CleanCoroutines
internal class MinionsCreationDirectiveProcessorTest {

    @RelaxedMockK
    lateinit var registry: DirectiveRegistry

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var scenariosKeeper: ScenariosKeeper

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
        every { scenariosKeeper.hasDag("my-scenario", "my-dag") } returns true

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
        every { scenariosKeeper.hasDag("my-scenario", "my-dag") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldPopNextMinionToCreateFromQueue() {
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
        runBlocking {
            processor.process(directive)
            // Wait for the directive to be fully processed.
            delay(20)
        }

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
    internal fun shouldPopNextMinionButDoNothingWhereThereIsNone() {
        // given
        val directive =
            MinionsCreationDirectiveReference(
                "my-directive",
                "my-campaign", "my-scenario", "my-dag"
            )
        coEvery {
            registry.pop<MinionId>(refEq(directive))
        } returns null

        // when
        runBlocking {
            processor.process(directive)
            // Wait for the directive to be fully completed.
            delay(20)
        }

        // then
        coVerify {
            registry.pop(directive)
        }
        confirmVerified(registry, minionsKeeper)
    }
}
