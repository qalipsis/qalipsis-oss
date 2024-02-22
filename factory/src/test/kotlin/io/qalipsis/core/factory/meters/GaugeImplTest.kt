package io.qalipsis.core.factory.meters

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@MicronautTest
@WithMockk
internal class GaugeImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    //measure(amount)

    @Test
    fun `value`() = testDispatcherProvider.run {
        // given
        val id = mockk<Meter.Id>()
        val gauge = GaugeImpl(id, meterReporter) { 9.0 }

        // when
        val result = gauge.value()

        // then
        assertThat(result).isEqualTo(9.0)
    }
}