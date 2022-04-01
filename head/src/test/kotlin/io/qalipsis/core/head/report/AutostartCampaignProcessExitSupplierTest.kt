package io.qalipsis.core.head.report

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.campaign.AutostartCampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class AutostartCampaignProcessExitSupplierTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var autostartCampaignConfiguration: AutostartCampaignConfiguration

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @InjectMockKs
    private lateinit var autostartCampaignProcessExitSupplier: AutostartCampaignProcessExitSupplier

    @Test
    internal fun `should not return any code when campaign was in success`() = testDispatcherProvider.runTest {
        // given
        every { autostartCampaignConfiguration.name } returns "my-campaign"
        coEvery { campaignReportStateKeeper.report("my-campaign").status } returns ExecutionStatus.SUCCESSFUL

        // when
        val result = autostartCampaignProcessExitSupplier.await()

        // then
        assertThat(result.isEmpty).isTrue()
    }

    @Test
    internal fun `should not return any code when campaign was with warning`() = testDispatcherProvider.runTest {
        // given
        every { autostartCampaignConfiguration.name } returns "my-campaign"
        coEvery { campaignReportStateKeeper.report("my-campaign").status } returns ExecutionStatus.WARNING

        // when
        val result = autostartCampaignProcessExitSupplier.await()

        // then
        assertThat(result.isEmpty).isTrue()
    }

    @Test
    internal fun `should return 201 when campaign failed`() = testDispatcherProvider.runTest {
        // given
        every { autostartCampaignConfiguration.name } returns "my-campaign"
        coEvery { campaignReportStateKeeper.report("my-campaign").status } returns ExecutionStatus.FAILED

        // when
        val result = autostartCampaignProcessExitSupplier.await()

        // then
        assertThat(result.get()).isEqualTo(201)
    }

    @Test
    internal fun `should return 202 when campaign was aborted`() = testDispatcherProvider.runTest {
        // given
        every { autostartCampaignConfiguration.name } returns "my-campaign"
        coEvery { campaignReportStateKeeper.report("my-campaign").status } returns ExecutionStatus.ABORTED

        // when
        val result = autostartCampaignProcessExitSupplier.await()

        // then
        assertThat(result.get()).isEqualTo(202)
    }
}