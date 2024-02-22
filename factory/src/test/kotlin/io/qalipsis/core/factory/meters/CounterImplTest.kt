package io.qalipsis.core.factory.meters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.factory.meters.catadioptre.value
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@MicronautTest
@WithMockk
internal class CounterImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    //measure(amount)

    @Test
    fun `should increment the counter by 1 when increment is called without an argument`() =
        testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val counter = CounterImpl(id, meterReporter)

            //when
            counter.increment()

            //then
            assertThat(counter.value()).isNotNull().all {
                prop(StepDouble::current).transform { it.toDouble() }.isEqualTo(1.0)
            }
        }

    @Test
    fun `should increment the counter by the argument passed in when increment is called`() =
        testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val counter = CounterImpl(id, meterReporter)

            //when
            counter.increment(5.0)

            //then
            assertThat(counter.value()).isNotNull().all {
                prop(StepDouble::current).transform { it.toDouble() }.isEqualTo(5.0)
            }
        }

    @Test
    fun `should return the cumulative count and reset value to zero`() =
        testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val counter = CounterImpl(id, meterReporter)
            assertThat(counter.count()).isEqualTo(0.0)
            counter.increment(5.0)
            counter.increment()
            counter.increment(2.0)
            assertThat(counter.value()).isNotNull().all {
                prop(StepDouble::current).transform { it.toDouble() }.isEqualTo(8.0)
            }

            //when
            val result = counter.count()

            //then
            assertThat(result).isEqualTo(8.0)
            assertThat(counter.value()).isNotNull().all {
                prop(StepDouble::current).transform { it.toDouble() }.isEqualTo(0.0)
            }
        }
}