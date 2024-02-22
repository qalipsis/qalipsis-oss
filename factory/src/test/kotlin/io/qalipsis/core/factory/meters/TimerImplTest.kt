package io.qalipsis.core.factory.meters

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.factory.meters.catadioptre.maxBucket
import io.qalipsis.core.math.round
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


@MicronautTest
@WithMockk
internal class TimerImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    //measure(amount)
    //histogramCountAtValue

    @Test
    fun `should return the max of the values when no unit is specified`() = testDispatcherProvider.run {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter)
        for (i in 3..8) {
            timer.record(Duration.of(i.toLong(), ChronoUnit.SECONDS))
        }

        //when
        val max = timer.max(null)

        //then
        assertThat(max).isEqualTo(8000000000.0)
    }

    @Test
    fun `should return the max of the values in the unit specified`() = testDispatcherProvider.run {
        //given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter)
        for (i in 1..5) {
            timer.record(Duration.of(i.toLong(), ChronoUnit.SECONDS))
        }

        //when
        val max = timer.max(TimeUnit.SECONDS)

        //then
        assertThat(max).isEqualTo(5.0)
    }

    @Test
    fun `should return the count`() = testDispatcherProvider.run {
        // given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter)
        timer.record(8000000000, TimeUnit.NANOSECONDS)
        timer.record(30000000, TimeUnit.MILLISECONDS)
        timer.record(2, TimeUnit.SECONDS)
        timer.record(1000000000, TimeUnit.NANOSECONDS)

        // when
        val count = timer.count()

        // then
        assertThat(count).isEqualTo(4)
    }

    @Test
    fun `should return the total time recorded`() = testDispatcherProvider.run {
        // given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter)
        timer.record(8000000000, TimeUnit.NANOSECONDS)
        timer.record(3000, TimeUnit.MILLISECONDS)
        timer.record(2, TimeUnit.SECONDS)
        timer.record(1000000000, TimeUnit.NANOSECONDS)

        // when
        val totalTime = timer.totalTime(null)

        // then
        assertThat(totalTime).isEqualTo(14000000000.0)
    }

    @Test
    fun `should return the total time recorded in the specified time unit`() = testDispatcherProvider.run {
        // given
        val id = mockk<Meter.Id>()
        val timer = TimerImpl(id, meterReporter)
        timer.record(8000000000, TimeUnit.NANOSECONDS)
        timer.record(3000, TimeUnit.MILLISECONDS)
        timer.record(2, TimeUnit.SECONDS)
        timer.record(1000000000, TimeUnit.NANOSECONDS)

        // when
        val totalTimeInNanoSeconds = timer.totalTime(TimeUnit.NANOSECONDS)
        val totalTimeInMicroSeconds = timer.totalTime(TimeUnit.MICROSECONDS)
        val totalTimeInMilliSeconds = timer.totalTime(TimeUnit.MILLISECONDS)
        val totalTimeInSeconds = timer.totalTime(TimeUnit.SECONDS)
        val totalTimeInMinutes = timer.totalTime(TimeUnit.MINUTES)
        val totalTimeInHours = timer.totalTime(TimeUnit.HOURS)
        val totalTimeInDays = timer.totalTime(TimeUnit.DAYS)

        // then
        assertThat(totalTimeInNanoSeconds).isEqualTo(14000000000.0)
        assertThat(totalTimeInMicroSeconds).isEqualTo(14000000.0)
        assertThat(totalTimeInMilliSeconds).isEqualTo(14000.0)
        assertThat(totalTimeInSeconds).isEqualTo(14.0)
        assertThat(totalTimeInMinutes.round(3)).isEqualTo(0.233)
        assertThat(totalTimeInHours.round(4)).isEqualTo(0.0039)
        assertThat(totalTimeInDays.round(4)).isEqualTo(0.0002)
    }

    @Test
    fun `should record the time in nanos when record is called with different time amounts of different units`() =
        testDispatcherProvider.run {
            // given
            val id = mockk<Meter.Id>()
            val timer = TimerImpl(id, meterReporter)

            // when
            timer.record(8000000000, TimeUnit.NANOSECONDS)
            timer.record(2, TimeUnit.SECONDS)
            timer.record(3000, TimeUnit.MILLISECONDS)

            // then
            assertThat(timer.maxBucket().size).isEqualTo(3)
            assertThat(timer.maxBucket().first()).isEqualTo(8000000000)
            assertThat(timer.maxBucket().contains(3000000000))
            assertThat(timer.maxBucket().last()).isEqualTo(2000000000)
        }

    @Test
    fun `should record the time in nanos when record is called with a duration`() =
        testDispatcherProvider.run {
            // given
            val id = mockk<Meter.Id>()
            val timer = TimerImpl(id, meterReporter)

            // when
            timer.record(Duration.ofSeconds(2))
            timer.record(Duration.ofNanos(98292829))
            timer.record(Duration.ofMillis(6724))


            // then
            assertThat(timer.maxBucket().size).isEqualTo(3)
            assertThat(timer.maxBucket().first()).isEqualTo(6724000000)
            assertThat(timer.maxBucket().contains(2000000000))
            assertThat(timer.maxBucket().last()).isEqualTo(98292829)
        }

    @Test
    fun `should record the time in nanos taken when record is called with a runnable`() =
        testDispatcherProvider.run {
            // given
            val id = mockk<Meter.Id>()
            val timer = TimerImpl(id, meterReporter)
            val beforeExecution: Long = System.nanoTime()

            // when
            timer.record {
                delay(10)
            }
            val afterExecution: Long = System.nanoTime()

            // then
            assertThat(timer.maxBucket().size).isEqualTo(1)
            assertThat(timer.maxBucket().first()).isLessThan(afterExecution - beforeExecution)
        }

    @Test
    fun `should return the percentile of the sample distribution`() =
        testDispatcherProvider.run {
            // given
            val id = mockk<Meter.Id>()
            val timer = TimerImpl(id, meterReporter)
            timer.record(Duration.ofSeconds(2))
            timer.record(Duration.ofNanos(98292829))
            timer.record(Duration.ofMillis(6724))
            timer.record(Duration.ofSeconds(6))
            timer.record(Duration.ofMillis(7727))
            timer.record(Duration.ofNanos(452678902))
            timer.record(Duration.ofNanos(452678902))

            // when
            val thirtiethPercentile = timer.percentile(0.3, TimeUnit.NANOSECONDS)
            val fiftiethPercentile = timer.percentile(0.5, TimeUnit.NANOSECONDS)
            val seventySixthPercentile = timer.percentile(0.76, TimeUnit.NANOSECONDS)

            // then
            assertThat(fiftiethPercentile).isEqualTo(2000000000.0)
            assertThat(thirtiethPercentile).isEqualTo(452678902.0)
            assertThat(seventySixthPercentile).isEqualTo(6724000000.0)
        }

    @Test
    fun `should return the mean of the sample distribution`() =
        testDispatcherProvider.run {
            // given
            val id = mockk<Meter.Id>()
            val timer = TimerImpl(id, meterReporter)
            timer.record(Duration.ofSeconds(2))
            timer.record(Duration.ofNanos(98292829))
            timer.record(Duration.ofMillis(6724))
            timer.record(Duration.ofSeconds(6))
            timer.record(Duration.ofMillis(7727))
            timer.record(Duration.ofNanos(452678902))
            timer.record(Duration.ofNanos(452678902))

            // when
            val sampleMean = timer.mean(TimeUnit.NANOSECONDS)

            // then
            assertThat(sampleMean).isEqualTo(3.350664376142857E9)
        }
}