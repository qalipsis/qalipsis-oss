package io.qalipsis.core.factory.orchestration.directives.processors.minions

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.directives.MinionsStartDirectiveReference
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.orchestration.ScenariosRegistry
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

/**
 * @author Eric Jessé
 */
@WithMockk
internal class MinionsStartDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var registry: DirectiveRegistry

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var scenariosRegistry: ScenariosRegistry

    @InjectMockKs
    lateinit var processor: MinionsStartDirectiveProcessor

    @Test
    @Timeout(1)
    internal fun shouldAcceptMinionsStartDirective() {
        val directive =
            MinionsStartDirectiveReference(
                "my-directive",
                "my-scenario",
                channel = "broadcast",
            )
        every { scenariosRegistry.contains("my-scenario") } returns true

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
            MinionsStartDirectiveReference(
                "my-directive",
                "my-scenario",
                channel = "broadcast",
            )
        every { scenariosRegistry.contains("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldListMinionsAndStartThem() = testCoroutineDispatcher.run {
        // given
        val directive =
            MinionsStartDirectiveReference(
                "my-directive",
                "my-scenario",
                channel = "broadcast",
            )
        coEvery {
            registry.list(refEq(directive))
        } returns listOf(
            MinionStartDefinition("my-minion-1", 123L),
            MinionStartDefinition("my-minion-2", 456L)
        )

        // when
        processor.process(directive)
        // Wait for the directive to be fully completed.
        delay(5)

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
    internal fun shouldListMinionsToStartButDoNothingWhereThereIsNone() = testCoroutineDispatcher.run {
        // given
        val directive =
            MinionsStartDirectiveReference(
                "my-directive",
                "my-scenario",
                channel = "broadcast",
            )
        coEvery {
            registry.list(refEq(directive))
        } returns emptyList()

        // when
        processor.process(directive)
        // Wait for the directive to be fully completed.
        delay(5)

        // then
        coVerify {
            registry.list(directive)
        }
        confirmVerified(registry, minionsKeeper)
    }

}
