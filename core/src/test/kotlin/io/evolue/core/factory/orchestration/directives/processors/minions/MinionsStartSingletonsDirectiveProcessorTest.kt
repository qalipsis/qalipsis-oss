package io.evolue.core.factory.orchestration.directives.processors.minions

import io.evolue.core.cross.driving.TestDescriptiveDirective
import io.evolue.core.cross.driving.directives.MinionsStartSingletonsDirective
import io.evolue.core.factory.orchestration.MinionsKeeper
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class MinionsStartSingletonsDirectiveProcessorTest {

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var scenariosKeeper: ScenariosKeeper

    @InjectMockKs
    lateinit var processor: MinionsStartSingletonsDirectiveProcessor

    @Test
    @Timeout(1)
    internal fun shouldAcceptMinionsStartDirective() {
        val directive =
            MinionsStartSingletonsDirective("my-scenario")
        every { scenariosKeeper.hasScenario("my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptNotMinionsStartDirective() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptMinionsStartDirectiveForUnknownDag() {
        val directive =
            MinionsStartSingletonsDirective("my-scenario")
        every { scenariosKeeper.hasScenario("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldStartSingletons() {
        // given
        val directive =
            MinionsStartSingletonsDirective("my-scenario")

        // when
        runBlocking {
            processor.process(directive)
            // Wait for the directive to be fully completed.
            delay(5)
        }

        // then
        coVerify {
            minionsKeeper.startSingletons("my-scenario")
        }
        confirmVerified(minionsKeeper)
    }

}