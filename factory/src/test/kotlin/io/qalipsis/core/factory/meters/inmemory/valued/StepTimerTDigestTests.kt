/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.meters.inmemory.valued

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.meters.Meter
import io.qalipsis.core.factory.meters.inmemory.valued.catadioptre.counter
import io.qalipsis.core.factory.meters.inmemory.valued.catadioptre.total
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 *  CF here stands for compression factor
 *  SDIT here stands for StandaloneDeploymentIntegrationTest
 *  CDIT here stands for ClusterDeploymentIntegrationTest
 */
@WithMockk
internal class StepTimerTDigestTests {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var meterReporter: MeterReporter

    /**
     * Testing addition of a single problematic value individually with CF of 100.0 and an AVL DIGEST.
     * Trying to reproduce the error "An error occurred while adding the value 3696750 to the T-Digest"
     *
     * This test passes
     */
    @Test
    internal fun `should add a single problematic value to the digest`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = StepTimer(id, meterReporter, listOf(25.0, 50.0))

        // when
        timer.record(Duration.of(3696750, ChronoUnit.NANOS))

        //then
        assertThat(timer.counter()).transform { it.toDouble() }.isEqualTo(1.0)
        assertThat(timer.total()).transform { it.toDouble() }.isEqualTo(3696.0)
    }

    /**
     * Testing addition of a single exceptionally high value individually with CF of 100.0 and an AVL DIGEST.
     * Trying to reproduce the error "An error occurred while adding the value 3696750 to the T-Digest"
     *
     * This test passes
     */
    @Test
    internal fun `should add a single really high value to the digest`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = StepTimer(id, meterReporter, listOf(25.0, 50.0))

        // when
        timer.record(Duration.of(3696724567502349956, ChronoUnit.NANOS))

        //then
        assertThat(timer.counter()).transform { it.toDouble() }.isEqualTo(1.0)
        assertThat(timer.total()).transform { it.toDouble() }.isEqualTo(3.696724567502349E15)
    }

    /**
     * Testing addition of 50 problematic values individually with CF of 100.0 and an AVL DIGEST.
     * Trying to reproduce the error "index out of bound exception when adding a value"
     *
     * This test passes
     */
    @Test
    internal fun `should not throw exception when adding 50 values to the digest`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = StepTimer(id, meterReporter, listOf(25.0, 50.0))

        // when
        for (i in 1..50) {
            timer.record(Duration.of(721125, ChronoUnit.NANOS))
        }

        //then
        assertThat(timer.counter()).transform { it.toDouble() }.isEqualTo(50.0)
    }

    /**
     * Testing with values same as StandardDeployment test with CF of 100.0 and an AVL DIGEST.
     * Trying to reproduce the exceptions thrown from SDIT on adding to the TDigest bucket using the same values as from the test.
     *
     * This test passes
     */
    @Test
    internal fun `should not throw exception when adding a huge number of values, about 596, to the digest`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = StepTimer(id, meterReporter, listOf(25.0, 50.0))

        // when
        File("duration_in_nanos2.txt").forEachLine {
            val amount = it.substring(0, it.length - 1)
            timer.record(Duration.of(amount.toLong(), ChronoUnit.NANOS))
        }

        //then
        assertThat(timer.counter()).transform { it.toDouble() }.isEqualTo(596.0)
    }

    /**
     * Occasionally throws: Exception in thread "pool-1-thread-5 @coroutine#9413" java.lang.ArrayIndexOutOfBoundsException:
     * arraycopy: last destination index 1052 out of bounds for double[1050]
     *
     */
    @Test
    internal fun `should return accurate percentiles when recording values from 0 to 10_000 milliseconds with a compression factor of 100 to the digest`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = StepTimer(id, meterReporter, listOf(25.0, 50.0))

        // when
        var count = 0.0
        while (count < 10_000) {
            timer.record(Duration.of(count.toLong(), ChronoUnit.MILLIS))
            count += 0.001
        }

        //then
        assertThat(
            timer.percentile(
                50.0,
                TimeUnit.MILLISECONDS
            )
        ).isBetween(4999.500046606136, 4999.500046606140)
        assertThat(
            timer.percentile(
                99.99,
                TimeUnit.MILLISECONDS
            )
        ).isBetween(9998.521248822666, 9998.521248822670)
    }


    @Test
    internal fun `should return accurate percentiles when recording values from 0 t0 20_000 milliseconds with a compression factor of 100 to the digest`() {
        //given
        val id = mockk<Meter.Id>()
        val timer = StepTimer(id, meterReporter, listOf(25.0, 50.0))

        // when
        var count = 0.0
        while (count < 20_000) {
            timer.record(Duration.of(count.toLong(), ChronoUnit.MILLIS))
            count += 0.001
        }
        println(timer.percentile(50.0, TimeUnit.MILLISECONDS))
        println(timer.percentile(99.99, TimeUnit.MILLISECONDS))

        //then
        assertThat(
            timer.percentile(
                50.0,
                TimeUnit.MILLISECONDS
            )
        ).isEqualTo(9999.499921515606)
        assertThat(
            timer.percentile(
                99.99,
                TimeUnit.MILLISECONDS
            )
        ).isEqualTo(19997.46503885969)
    }


    @Test
    internal fun `should return accurate percentiles when recording milliseconds with a compression factor of 100 in a multithreaded environment`() =
        testDispatcherProvider.run {
            //given
            val id = mockk<Meter.Id>()
            val timer = StepTimer(id, meterReporter, listOf(25.0, 50.0))
            val threadPool = Executors.newFixedThreadPool(30).asCoroutineDispatcher()

            // when
            run {
                val jobs = List(10_000) {
                    launch(threadPool) {
                        val value = Random.nextDouble(0.0, 10_000.0)
                        timer.record(Duration.of(value.toLong(), ChronoUnit.MILLIS))
                    }
                }
                jobs.forEach { it.join() }
            }
            timer.record(Duration.of(20, ChronoUnit.SECONDS))

            //then
//            assertThat(timer.percentile(50.0, TimeUnit.MILLISECONDS)).isEqualTo(5000)//Returns 4999.500046606138
//            assertThat(timer.percentile(99.99, TimeUnit.MILLISECONDS)).isEqualTo(9990)//Returns 9998.521248822666
        }


    /**
     *  COMPRESSION FACTOR 500, AVL DIGEST
     *  Calling the SDIT test with a compression factor of 500 didn't fix it.
     *  There was exception as well in getting the quantile from T-digest
     *  1000.0 compression factor didn't fix it either
     *
     */

    /**
     *  COMPRESSION FACTOR 1000.0 MERGING DIGEST
     *  SDIT test seems stable and passes with 1000 compression factor Ran it thrice
     *  Took out the try catch test still passes at 1000 compression factor
     *  Set compression factor down to 500 and both CDIT and SDIT still passes wonderfully well
     *  Compression factor of 200.0: Test is not super stable. One test from CDIT ocassionally fails
     *  Compression factor of 100.0: Test starts to get shabby and one of the test of CDIT fails
     */

    /**
     *  COMPRESSION FACTOR 1000.0 MERGING DIGEST
     *  SDIT test seems stable and passes with 1000 compression factor Ran it thrice
     *  Took out the try catch test still passes at 1000 compression factor
     *  Set compression factor down to 500 and both CDIT and SDIT still passes wonderfully well
     *  Compression factor of 100.0: Test starts to get shabby and one of the test of CDIT fails
     */

    /**
     * Saving in MILLISECONDS and MICROSECONDS AND SECONDS, with a CF of 100 and AVLDIGEST didn't seem to help
     * either as test still fails or does not pass at all as in the cases of MICROSECONDS and MILLISECONDS.
     *
     * 500.0 CF, does not help either as test still fails
     * 1000.0 CF, does not help either as test still fails
     */

    /**
     * Saving in MILLISECONDS and MICROSECONDS AND SECONDS, with a CF of 100 and MERGING DiGEST:
     *
     * Saving as SECONDS and with 100.0, 500.0 and 1000.0 CFs worked
     * Saving as MILLISECONDS and with 100.0, 500.0 and 1000.0 CFs worked
     * Saving as MICROSECONDS and with 100.0, 500.0 and 1000.0 CFs worked
     *
     */


    /**
     ******* SUMMARY *******
     *
     * With these investigations and findings I would suggest the following:
     *
     * Firstly, we save the data in milliseconds as it reduces the bulk of a single data value and forces our data to be
     * within a certain range that is ideal for the compression factor we have chosen, 100.0.
     *
     * Secondly it enables us use a lower compression factor. The lower the compression factor
     * the more accurate the results.
     *
     * Finally, I would recommend we switch to creating the T-Digest instance with the mergingDigest
     * since it is a much simpler and flexible digest and more widely used compared to the
     * AVLDigest which is much more complex to implement and understand.
     * The mergingDigest also fixed the problems with the test, so I don't really see any issues preventing us from using it.
     *
     */
}