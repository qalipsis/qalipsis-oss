package io.qalipsis.core.factory.orchestration.directives.processors.minions

import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.core.directives.CampaignStartDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.orchestration.ScenariosRegistry
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignStartDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var scenariosRegistry: ScenariosRegistry

    @InjectMockKs
    lateinit var processorStart: CampaignStartDirectiveProcessor

    @Test
    @Timeout(1)
    internal fun shouldAcceptMinionsStartDirective() {
        val directive =
            CampaignStartDirective("my-campaign", "my-scenario", channel = "broadcast", key = TestIdGenerator.short())
        every { scenariosRegistry.contains("my-scenario") } returns true

        Assertions.assertTrue(processorStart.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptNotMinionsStartDirective() {
        Assertions.assertFalse(processorStart.accept(TestDescriptiveDirective()))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptMinionsStartDirectiveForUnknownDag() {
        val directive =
            CampaignStartDirective("my-campaign", "my-scenario", channel = "broadcast", key = TestIdGenerator.short())
        every { scenariosRegistry.contains("my-scenario") } returns false

        Assertions.assertFalse(processorStart.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldStartSingletons() = testCoroutineDispatcher.run {
        // given
        val directive =
            CampaignStartDirective("my-campaign", "my-scenario", channel = "broadcast", key = TestIdGenerator.short())

        // when
        processorStart.process(directive)
        // Wait for the directive to be fully completed.
        delay(5)

        // then
        coVerify {
            minionsKeeper.startCampaign("my-campaign", "my-scenario")
        }
        confirmVerified(minionsKeeper)
    }

}
