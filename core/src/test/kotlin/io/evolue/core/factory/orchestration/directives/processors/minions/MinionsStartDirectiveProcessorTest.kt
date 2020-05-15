package io.evolue.core.factory.orchestration.directives.processors.minions

import io.evolue.core.cross.driving.TestDescriptiveDirective
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.core.cross.driving.directives.MinionStartDefinition
import io.evolue.core.cross.driving.directives.MinionsStartDirectiveReference
import io.evolue.core.factory.orchestration.MinionsKeeper
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.mockk.coEvery
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
import java.time.Instant

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class MinionsStartDirectiveProcessorTest {

    @RelaxedMockK
    lateinit var registry: DirectiveRegistry

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var scenariosKeeper: ScenariosKeeper

    @InjectMockKs
    lateinit var processor: MinionsStartDirectiveProcessor

    @Test
    @Timeout(1)
    internal fun shouldAcceptMinionsStartDirective() {
        val directive =
            MinionsStartDirectiveReference("my-directive",
                "my-scenario")
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
            MinionsStartDirectiveReference("my-directive",
                "my-scenario")
        every { scenariosKeeper.hasScenario("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldListMinionsAndStartThem() {
        // given
        val directive =
            MinionsStartDirectiveReference("my-directive",
                "my-scenario")
        coEvery {
            registry.list(refEq(directive))
        } returns listOf(
            MinionStartDefinition("my-minion-1", 123L),
            MinionStartDefinition("my-minion-2", 456L))

        // when
        runBlocking {
            processor.process(directive)
            // Wait for the directive to be fully completed.
            delay(5)
        }

        // then
        coVerify {
            registry.list(directive)
            minionsKeeper.startMinionAt("my-minion-1", Instant.ofEpochMilli(123))
            minionsKeeper.startMinionAt("my-minion-2", Instant.ofEpochMilli(456))
        }
        confirmVerified(registry, minionsKeeper)
    }

    @Test
    @Timeout(1)
    internal fun shouldListMinionsToStartButDoNothingWhereThereIsNone() {
        // given
        val directive =
            MinionsStartDirectiveReference("my-directive",
                "my-scenario")
        coEvery {
            registry.list(refEq(directive))
        } returns emptyList()

        // when
        runBlocking {
            processor.process(directive)
            // Wait for the directive to be fully completed.
            delay(5)
        }

        // then
        coVerify {
            registry.list(directive)
        }
        confirmVerified(registry, minionsKeeper)
    }

}