/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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
package io.qalipsis.core.factory.meters.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.key
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.core.factory.meters.CompositeCounter
import io.qalipsis.core.factory.meters.CompositeDistributionSummary
import io.qalipsis.core.factory.meters.CompositeGauge
import io.qalipsis.core.factory.meters.CompositeTimer
import io.qalipsis.core.factory.meters.inmemory.catadioptre.globalMeters
import io.qalipsis.core.factory.meters.inmemory.catadioptre.meters
import io.qalipsis.core.reporter.MeterReporter
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class InMemoryCumulativeMeterRegistryTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    lateinit var meterReporter: MeterReporter

    @InjectMockKs
    lateinit var registry: InMemoryCumulativeMeterRegistry

    @AfterEach
    fun tearDown() = testDispatcherProvider.runTest {
        registry.meters().clear()
        registry.globalMeters().clear()
        unmockkConstructor(
            InMemoryCumulativeTimer::class,
            InMemoryCumulativeDistributionSummary::class,
            InMemoryCumulativeCounter::class,
            InMemoryGauge::class
        )
    }

    @Test
    fun `should create a timer without percentiles`() = testDispatcherProvider.runTest {
        // given
        registry.init(mockk { every { scenarios.size } returns 1 })
        val id = mockk<Meter.Id>()

        // when
        val meter = registry.timer(id)

        // then
        assertThat(meter).isInstanceOf<InMemoryCumulativeTimer>().all {
            prop(Meter<*>::id).isSameAs(id)
            prop("meterReporter").isSameAs(meterReporter)
            typedProp<Collection<Double>>("percentiles").isEmpty()
        }
    }

    @Test
    fun `should create a timer with percentiles`() = testDispatcherProvider.runTest {
        // given
        registry.init(mockk { every { scenarios.size } returns 1 })
        val id = mockk<Meter.Id>()

        // when
        val meter = registry.timer(id, setOf(20.0, 55.0))

        // then
        assertThat(meter).isInstanceOf<InMemoryCumulativeTimer>().all {
            prop(Meter<*>::id).isSameAs(id)
            prop("meterReporter").isSameAs(meterReporter)
            typedProp<Collection<Double>>("percentiles").all {
                hasSize(2)
                containsAll(20.0, 55.0)
            }
        }
        assertThat(registry.meters()).key(id).isSameAs(meter)
    }

    @Test
    fun `should create a timer without percentiles when there are several scenarios and the meter has no scenario tag`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id = mockk<Meter.Id> { every { tags.containsKey("scenario") } returns false }

            // when
            val meter = registry.timer(id)

            // then
            assertThat(meter).isInstanceOf<InMemoryCumulativeTimer>().all {
                prop(Meter<*>::id).isSameAs(id)
                prop("meterReporter").isSameAs(meterReporter)
                typedProp<Collection<Double>>("percentiles").isEmpty()
            }
        }

    @Test
    fun `should create a timer with percentiles when there are several scenarios and the meter has no scenario tag`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id = mockk<Meter.Id> { every { tags.containsKey("scenario") } returns false }

            // when
            val meter = registry.timer(id, setOf(20.0, 55.0))

            // then
            assertThat(meter).isInstanceOf<InMemoryCumulativeTimer>().all {
                prop(Meter<*>::id).isSameAs(id)
                prop("meterReporter").isSameAs(meterReporter)
                typedProp<Collection<Double>>("percentiles").all {
                    hasSize(2)
                    containsAll(20.0, 55.0)
                }
            }
            assertThat(registry.meters()).key(id).isSameAs(meter)
        }

    @Test
    fun `should create a composite timer without percentiles when there are several scenarios and the meter has the scenario tag`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id1 = Meter.Id("test", MeterType.TIMER, mapOf("a" to "b", "c" to "d", "scenario" to "the scenario"))

            // when
            val meter1 = registry.timer(id1)

            // then
            assertThat(meter1).isInstanceOf<CompositeTimer>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeTimer>().all {
                    prop(Meter<*>::id).isSameAs(id1)
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").isEmpty()
                }
                prop("globalMeter").isNotNull().isInstanceOf<InMemoryCumulativeTimer>().all {
                    prop(Meter<*>::id).isEqualTo(Meter.Id("test", MeterType.TIMER, mapOf("a" to "b", "c" to "d")))
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").isEmpty()
                }
            }

            assertThat(registry.meters().size).isEqualTo(1)
            val (id, meter) = registry.meters().toList()[0]
            assertThat(id).all {
                prop(Meter.Id::type).isEqualTo(MeterType.TIMER)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(3)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                    key("scenario").isEqualTo("the scenario")
                }
            }
            assertThat(meter).isInstanceOf(CompositeTimer::class.java).all {
                prop(CompositeTimer::id).isEqualTo(id1)
                prop(CompositeTimer::percentiles).isEmpty()
            }

            assertThat(registry.globalMeters().size).isEqualTo(1)
            val (globalId, globalMeter) = registry.globalMeters().toList()[0]
            assertThat(globalId).all {
                prop(Meter.Id::type).isEqualTo(MeterType.TIMER)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(2)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                }
            }
            assertThat(globalMeter).isInstanceOf(InMemoryCumulativeTimer::class.java).all {
                prop(InMemoryCumulativeTimer::id).isEqualTo(id1.copy(tags = id1.tags - "scenario"))
                prop(InMemoryCumulativeTimer::percentiles).isEmpty()
            }

            // when
            val id2 =
                Meter.Id("test", MeterType.TIMER, mapOf("a" to "b", "c" to "d", "scenario" to "the other scenario"))
            val meter2 = registry.timer(id2)

            // then
            assertThat(meter2).isInstanceOf<CompositeTimer>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeTimer>().all {
                    prop(Meter<*>::id).isSameAs(id2)
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").isEmpty()
                }
                prop("globalMeter").isNotNull()
                    .isSameAs(meter1.getProperty("globalMeter")) // The global meter should be reused.
            }
            assertThat(registry.meters().size).isEqualTo(2)
            assertThat(registry.meters()[id2]).isNotNull().all {
                isInstanceOf(CompositeTimer::class.java).all {
                    prop(CompositeTimer::id).isEqualTo(id2)
                    prop(CompositeTimer::percentiles).isEmpty()
                }
            }

            assertThat(registry.meters()).hasSize(2)
            assertThat(registry.globalMeters()).hasSize(1)
        }

    @Test
    fun `should create a composite timer with percentiles when there are several scenarios`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id1 = Meter.Id("test", MeterType.TIMER, mapOf("a" to "b", "c" to "d", "scenario" to "the scenario"))

            // when
            val meter1 = registry.timer(id1, setOf(20.0, 55.0))

            // then
            assertThat(meter1).isInstanceOf<CompositeTimer>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeTimer>().all {
                    prop(Meter<*>::id).isSameAs(id1)
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").all {
                        hasSize(2)
                        containsAll(20.0, 55.0)
                    }
                }
                prop("globalMeter").isNotNull().isInstanceOf<InMemoryCumulativeTimer>().all {
                    prop(Meter<*>::id).isEqualTo(Meter.Id("test", MeterType.TIMER, mapOf("a" to "b", "c" to "d")))
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").all {
                        hasSize(2)
                        containsAll(20.0, 55.0)
                    }
                }
            }

            assertThat(registry.meters().size).isEqualTo(1)
            val (id, meter) = registry.meters().toList()[0]
            assertThat(id).all {
                prop(Meter.Id::type).isEqualTo(MeterType.TIMER)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(3)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                    key("scenario").isEqualTo("the scenario")
                }
            }
            assertThat(meter).isInstanceOf(CompositeTimer::class.java).all {
                prop(CompositeTimer::id).isEqualTo(id1)
                prop(CompositeTimer::percentiles).containsAll(20.0, 55.0)
            }

            assertThat(registry.globalMeters().size).isEqualTo(1)
            val (globalId, globalMeter) = registry.globalMeters().toList()[0]
            assertThat(globalId).all {
                prop(Meter.Id::type).isEqualTo(MeterType.TIMER)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(2)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                }
            }
            assertThat(globalMeter).isInstanceOf(InMemoryCumulativeTimer::class.java).all {
                prop(InMemoryCumulativeTimer::id).isEqualTo(id1.copy(tags = id1.tags - "scenario"))
                prop(InMemoryCumulativeTimer::percentiles).containsAll(20.0, 55.0)
            }

            // when
            val id2 =
                Meter.Id("test", MeterType.TIMER, mapOf("a" to "b", "c" to "d", "scenario" to "the other scenario"))
            val meter2 = registry.timer(id2, setOf(21.0, 86.0))

            // then
            assertThat(meter2).isInstanceOf<CompositeTimer>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeTimer>().all {
                    prop(Meter<*>::id).isSameAs(id2)
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").all {
                        hasSize(2)
                        containsAll(21.0, 86.0)
                    }
                }
                prop("globalMeter").isNotNull()
                    .isSameAs(meter1.getProperty("globalMeter")) // The global meter should be reused.
            }
            assertThat(registry.meters().size).isEqualTo(2)
            assertThat(registry.meters()[id2]).isNotNull().all {
                isInstanceOf(CompositeTimer::class.java).all {
                    prop(CompositeTimer::id).isEqualTo(id2)
                    prop(CompositeTimer::percentiles).containsAll(21.0, 86.0)
                }
            }

            assertThat(registry.meters()).hasSize(2)
            assertThat(registry.globalMeters()).hasSize(1)
        }

    @Test
    fun `should create a gauge`() = testDispatcherProvider.runTest {
        // given
        registry.init(mockk { every { scenarios.size } returns 1 })
        val id = mockk<Meter.Id>()

        // when
        val meter = registry.gauge(id)

        // then
        assertThat(meter).isInstanceOf<InMemoryGauge>().all {
            prop(Meter<*>::id).isSameAs(id)
            prop("meterReporter").isSameAs(meterReporter)
        }
        assertThat(registry.meters()).key(id).isSameAs(meter)
    }

    @Test
    fun `should create a gauge when there are several scenarios and the meter has no scenario tag`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id = mockk<Meter.Id> { every { tags.contains("scenario") } returns false }

            // when
            val meter = registry.gauge(id)

            // then
            assertThat(meter).isInstanceOf<InMemoryGauge>().all {
                prop(Meter<*>::id).isSameAs(id)
                prop("meterReporter").isSameAs(meterReporter)
            }
            assertThat(registry.meters()).key(id).isSameAs(meter)
        }

    @Test
    fun `should create a composite gauge when there are several scenarios`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id1 = Meter.Id("test", MeterType.GAUGE, mapOf("a" to "b", "c" to "d", "scenario" to "the scenario"))

            // when
            val meter1 = registry.gauge(id1)

            // then
            assertThat(meter1).isInstanceOf<CompositeGauge>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryGauge>().all {
                    prop(Meter<*>::id).isSameAs(id1)
                    prop("meterReporter").isSameAs(meterReporter)
                }
                prop("globalMeter").isNotNull().isInstanceOf<InMemoryGauge>().all {
                    prop(Meter<*>::id).isEqualTo(Meter.Id("test", MeterType.GAUGE, mapOf("a" to "b", "c" to "d")))
                    prop("meterReporter").isSameAs(meterReporter)
                }
            }

            assertThat(registry.meters().size).isEqualTo(1)
            val (id, meter) = registry.meters().toList()[0]
            assertThat(id).all {
                prop(Meter.Id::type).isEqualTo(MeterType.GAUGE)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(3)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                    key("scenario").isEqualTo("the scenario")
                }
            }
            assertThat(meter).isInstanceOf(CompositeGauge::class.java).all {
                prop(CompositeGauge::id).isEqualTo(id1)
            }

            assertThat(registry.globalMeters().size).isEqualTo(1)
            val (globalId, globalMeter) = registry.globalMeters().toList()[0]
            assertThat(globalId).all {
                prop(Meter.Id::type).isEqualTo(MeterType.GAUGE)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(2)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                }
            }
            assertThat(globalMeter).isInstanceOf(InMemoryGauge::class.java).all {
                prop(InMemoryGauge::id).isEqualTo(id1.copy(tags = id1.tags - "scenario"))
            }

            // when
            val id2 =
                Meter.Id("test", MeterType.GAUGE, mapOf("a" to "b", "c" to "d", "scenario" to "the other scenario"))
            val meter2 = registry.gauge(id2)

            // then
            assertThat(meter2).isInstanceOf<CompositeGauge>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryGauge>().all {
                    prop(Meter<*>::id).isSameAs(id2)
                    prop("meterReporter").isSameAs(meterReporter)
                }
                prop("globalMeter").isNotNull()
                    .isSameAs(meter1.getProperty("globalMeter")) // The global meter should be reused.
            }
            assertThat(registry.meters()[id2]).isNotNull().all {
                isInstanceOf(CompositeGauge::class.java).all {
                    prop(CompositeGauge::id).isEqualTo(id2)
                }
            }

            assertThat(registry.meters()).hasSize(2)
            assertThat(registry.globalMeters()).hasSize(1)
        }

    @Test
    fun `should create a distribution summary without percentiles`() = testDispatcherProvider.runTest {
        // given
        registry.init(mockk { every { scenarios.size } returns 1 })
        val id = mockk<Meter.Id>()

        // when
        val meter = registry.summary(id)

        // then
        assertThat(meter).isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
            prop(Meter<*>::id).isSameAs(id)
            prop("meterReporter").isSameAs(meterReporter)
            typedProp<Collection<Double>>("percentiles").isEmpty()
        }
        assertThat(registry.meters()).key(id).isSameAs(meter)
    }

    @Test
    fun `should create a distribution summary with percentiles`() = testDispatcherProvider.runTest {
        // given
        registry.init(mockk { every { scenarios.size } returns 1 })
        val id = mockk<Meter.Id>()

        // when
        val meter = registry.summary(id, setOf(20.0, 55.0))

        // then
        assertThat(meter).isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
            prop(Meter<*>::id).isSameAs(id)
            prop("meterReporter").isSameAs(meterReporter)
            typedProp<Collection<Double>>("percentiles").all {
                hasSize(2)
                containsAll(20.0, 55.0)
            }
        }
    }

    @Test
    fun `should create a distribution summary without percentiles when there are several scenarios and the meter has no scenario tag`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id = mockk<Meter.Id> { every { tags.containsKey("scenario") } returns false }

            // when
            val meter = registry.summary(id)

            // then
            assertThat(meter).isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
                prop(Meter<*>::id).isSameAs(id)
                prop("meterReporter").isSameAs(meterReporter)
                typedProp<Collection<Double>>("percentiles").isEmpty()
            }
            assertThat(registry.meters()).key(id).isSameAs(meter)
        }

    @Test
    fun `should create a distribution summary with percentiles when there are several scenarios and the meter has no scenario tag`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id = mockk<Meter.Id> { every { tags.containsKey("scenario") } returns false }

            // when
            val meter = registry.summary(id, setOf(20.0, 55.0))

            // then
            assertThat(meter).isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
                prop(Meter<*>::id).isSameAs(id)
                prop("meterReporter").isSameAs(meterReporter)
                typedProp<Collection<Double>>("percentiles").all {
                    hasSize(2)
                    containsAll(20.0, 55.0)
                }
            }
        }

    @Test
    fun `should create a composite distribution summary without percentiles when there are several scenarios and the meter has the scenario tag`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id1 = Meter.Id(
                "test",
                MeterType.DISTRIBUTION_SUMMARY,
                mapOf("a" to "b", "c" to "d", "scenario" to "the scenario")
            )

            // when
            val meter1 = registry.summary(id1)

            // then
            assertThat(meter1).isInstanceOf<CompositeDistributionSummary>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
                    prop(Meter<*>::id).isSameAs(id1)
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").isEmpty()
                }
                prop("globalMeter").isNotNull().isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
                    prop(Meter<*>::id).isEqualTo(
                        Meter.Id(
                            "test",
                            MeterType.DISTRIBUTION_SUMMARY,
                            mapOf("a" to "b", "c" to "d")
                        )
                    )
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").isEmpty()
                }
            }

            assertThat(registry.meters().size).isEqualTo(1)
            val (id, meter) = registry.meters().toList()[0]
            assertThat(id).all {
                prop(Meter.Id::type).isEqualTo(MeterType.DISTRIBUTION_SUMMARY)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(3)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                    key("scenario").isEqualTo("the scenario")
                }
            }
            assertThat(meter).isInstanceOf(CompositeDistributionSummary::class.java).all {
                prop(CompositeDistributionSummary::id).isEqualTo(id1)
            }

            assertThat(registry.globalMeters().size).isEqualTo(1)
            val (globalId, globalMeter) = registry.globalMeters().toList()[0]
            assertThat(globalId).all {
                prop(Meter.Id::type).isEqualTo(MeterType.DISTRIBUTION_SUMMARY)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(2)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                }
            }
            assertThat(globalMeter).isInstanceOf(InMemoryCumulativeDistributionSummary::class.java).all {
                prop(InMemoryCumulativeDistributionSummary::id).isEqualTo(id1.copy(tags = id1.tags - "scenario"))
            }

            // when
            val id2 = Meter.Id(
                "test",
                MeterType.DISTRIBUTION_SUMMARY,
                mapOf("a" to "b", "c" to "d", "scenario" to "the other scenario")
            )
            val meter2 = registry.summary(id2)

            // then
            assertThat(meter2).isInstanceOf<CompositeDistributionSummary>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
                    prop(Meter<*>::id).isSameAs(id2)
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").isEmpty()
                }
                prop("globalMeter").isNotNull()
                    .isSameAs(meter1.getProperty("globalMeter")) // The global meter should be reused.
            }
            assertThat(registry.meters().size).isEqualTo(2)
            assertThat(registry.meters()[id2]).isNotNull().all {
                isInstanceOf(CompositeDistributionSummary::class.java).all {
                    prop(CompositeDistributionSummary::id).isEqualTo(id2)
                }
            }
            assertThat(registry.meters()).hasSize(2)
            assertThat(registry.globalMeters()).hasSize(1)
        }

    @Test
    fun `should create a composite distribution summary with percentiles when there are several scenarios`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id1 = Meter.Id(
                "test",
                MeterType.DISTRIBUTION_SUMMARY,
                mapOf("a" to "b", "c" to "d", "scenario" to "the scenario")
            )

            // when
            val meter1 = registry.summary(id1, setOf(20.0, 55.0))

            // then
            assertThat(meter1).isInstanceOf<CompositeDistributionSummary>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
                    prop(Meter<*>::id).isSameAs(id1)
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").all {
                        hasSize(2)
                        containsAll(20.0, 55.0)
                    }
                }
                prop("globalMeter").isNotNull().isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
                    prop(Meter<*>::id).isEqualTo(
                        Meter.Id(
                            "test",
                            MeterType.DISTRIBUTION_SUMMARY,
                            mapOf("a" to "b", "c" to "d")
                        )
                    )
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").all {
                        hasSize(2)
                        containsAll(20.0, 55.0)
                    }
                }
            }
            assertThat(registry.meters().size).isEqualTo(1)
            val (id, meter) = registry.meters().toList()[0]
            assertThat(id).all {
                prop(Meter.Id::type).isEqualTo(MeterType.DISTRIBUTION_SUMMARY)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(3)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                    key("scenario").isEqualTo("the scenario")
                }
            }
            assertThat(meter).isInstanceOf(CompositeDistributionSummary::class.java).all {
                prop(CompositeDistributionSummary::id).isEqualTo(id1)
                prop(CompositeDistributionSummary::percentiles).containsAll(20.0, 55.0)
            }

            assertThat(registry.globalMeters().size).isEqualTo(1)
            val (globalId, globalMeter) = registry.globalMeters().toList()[0]
            assertThat(globalId).all {
                prop(Meter.Id::type).isEqualTo(MeterType.DISTRIBUTION_SUMMARY)
                prop(Meter.Id::meterName).isEqualTo("test")
                typedProp<Map<String, String>>("tags").all {
                    hasSize(2)
                    key("a").isEqualTo("b")
                    key("c").isEqualTo("d")
                }
            }
            assertThat(globalMeter).isInstanceOf(InMemoryCumulativeDistributionSummary::class.java).all {
                prop(InMemoryCumulativeDistributionSummary::id).isEqualTo(id1.copy(tags = id1.tags - "scenario"))
                prop(InMemoryCumulativeDistributionSummary::percentiles).containsAll(20.0, 55.0)
            }

            // when
            val id2 = Meter.Id(
                "test",
                MeterType.DISTRIBUTION_SUMMARY,
                mapOf("a" to "b", "c" to "d", "scenario" to "the other scenario")
            )
            val meter2 = registry.summary(id2, setOf(21.0, 35.0))

            // then
            assertThat(meter2).isInstanceOf<CompositeDistributionSummary>().all {
                prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeDistributionSummary>().all {
                    prop(Meter<*>::id).isSameAs(id2)
                    prop("meterReporter").isSameAs(meterReporter)
                    typedProp<Collection<Double>>("percentiles").all {
                        hasSize(2)
                        containsAll(21.0, 35.0)
                    }
                }
                prop("globalMeter").isNotNull()
                    .isSameAs(meter1.getProperty("globalMeter")) // The global meter should be reused.
            }
            assertThat(registry.meters().size).isEqualTo(2)
            assertThat(registry.meters()[id2]).isNotNull().all {
                isInstanceOf(CompositeDistributionSummary::class.java).all {
                    prop(CompositeDistributionSummary::id).isEqualTo(id2)
                    prop(CompositeDistributionSummary::percentiles).containsAll(21.0, 35.0)
                }
            }
            assertThat(registry.meters()).hasSize(2)
            assertThat(registry.globalMeters()).hasSize(1)
        }

    @Test
    fun `should create a counter`() = testDispatcherProvider.runTest {
        // given
        registry.init(mockk { every { scenarios.size } returns 1 })
        val id = mockk<Meter.Id>()

        // when
        val meter = registry.counter(id)

        // then
        assertThat(meter).isInstanceOf<InMemoryCumulativeCounter>().all {
            prop(Meter<*>::id).isSameAs(id)
            prop("meterReporter").isSameAs(meterReporter)
        }
        assertThat(registry.meters()).key(id).isSameAs(meter)
    }

    @Test
    fun `should create a counter when there are several scenarios and the meter has no scenario tag`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val id = mockk<Meter.Id> { every { tags.contains("scenario") } returns false }

            // when
            val meter = registry.counter(id)

            // then
            assertThat(meter).isInstanceOf<InMemoryCumulativeCounter>().all {
                prop(Meter<*>::id).isSameAs(id)
                prop("meterReporter").isSameAs(meterReporter)
            }
            assertThat(registry.meters()).key(id).isSameAs(meter)
        }

    @Test
    fun `should create a composite counter when there are several scenarios`() = testDispatcherProvider.runTest {
        // given
        registry.init(mockk { every { scenarios.size } returns 2 })
        val id1 = Meter.Id("test", MeterType.COUNTER, mapOf("a" to "b", "c" to "d", "scenario" to "the scenario"))

        // when
        val meter1 = registry.counter(id1)

        // then
        assertThat(meter1).isInstanceOf<CompositeCounter>().all {
            prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeCounter>().all {
                prop(Meter<*>::id).isSameAs(id1)
                prop("meterReporter").isSameAs(meterReporter)
            }
            prop("globalMeter").isNotNull().isInstanceOf<InMemoryCumulativeCounter>().all {
                prop(Meter<*>::id).isEqualTo(Meter.Id("test", MeterType.COUNTER, mapOf("a" to "b", "c" to "d")))
                prop("meterReporter").isSameAs(meterReporter)
            }
        }
        assertThat(registry.meters().size).isEqualTo(1)
        val (id, meter) = registry.meters().toList()[0]
        assertThat(id).all {
            prop(Meter.Id::type).isEqualTo(MeterType.COUNTER)
            prop(Meter.Id::meterName).isEqualTo("test")
            typedProp<Map<String, String>>("tags").all {
                hasSize(3)
                key("a").isEqualTo("b")
                key("c").isEqualTo("d")
                key("scenario").isEqualTo("the scenario")
            }
        }
        assertThat(meter).isInstanceOf(CompositeCounter::class.java).all {
            prop(CompositeCounter::id).isEqualTo(id1)
        }

        assertThat(registry.globalMeters().size).isEqualTo(1)
        val (globalId, globalMeter) = registry.globalMeters().toList()[0]
        assertThat(globalId).all {
            prop(Meter.Id::type).isEqualTo(MeterType.COUNTER)
            prop(Meter.Id::meterName).isEqualTo("test")
            typedProp<Map<String, String>>("tags").all {
                hasSize(2)
                key("a").isEqualTo("b")
                key("c").isEqualTo("d")
            }
        }
        assertThat(globalMeter).isInstanceOf(InMemoryCumulativeCounter::class.java).all {
            prop(InMemoryCumulativeCounter::id).isEqualTo(id1.copy(tags = id1.tags - "scenario"))
        }

        // when
        val id2 =
            Meter.Id("test", MeterType.COUNTER, mapOf("a" to "b", "c" to "d", "scenario" to "the other scenario"))
        val meter2 = registry.counter(id2)

        // then
        assertThat(meter2).isInstanceOf<CompositeCounter>().all {
            prop("scenarioMeter").isNotNull().isInstanceOf<InMemoryCumulativeCounter>().all {
                prop(Meter<*>::id).isSameAs(id2)
                prop("meterReporter").isSameAs(meterReporter)
            }
            prop("globalMeter").isNotNull()
                .isSameAs(meter1.getProperty("globalMeter")) // The global meter should be reused.
        }
        assertThat(registry.meters().size).isEqualTo(2)
        assertThat(registry.meters()[id2]).isNotNull().all {
            isInstanceOf(CompositeCounter::class.java).all {
                prop(CompositeCounter::id).isEqualTo(id2)
            }
        }
        assertThat(registry.meters()).hasSize(2)
        assertThat(registry.globalMeters()).hasSize(1)
    }

    @Test
    fun `should generate snapshots from all the registered meters when there are several scenarios`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val timerId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.TIMER, emptyMap())
            val counterId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.COUNTER, emptyMap())
            val gaugeId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.GAUGE, emptyMap())
            val summaryId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.DISTRIBUTION_SUMMARY, emptyMap())
            mockkConstructor(
                InMemoryCumulativeTimer::class,
                InMemoryCumulativeDistributionSummary::class,
                InMemoryCumulativeCounter::class,
                InMemoryGauge::class
            )
            val timerSnapshot = mockk<MeterSnapshot>()
            coEvery { anyConstructed<InMemoryCumulativeTimer>().snapshot(any()) } returns timerSnapshot
            val gaugeSnapshot = mockk<MeterSnapshot>()
            coEvery { anyConstructed<InMemoryGauge>().snapshot(any()) } returns gaugeSnapshot
            val counterSnapshot = mockk<MeterSnapshot>()
            coEvery { anyConstructed<InMemoryCumulativeCounter>().snapshot(any()) } returns counterSnapshot
            val summarySnapshot = mockk<MeterSnapshot>()
            coEvery { anyConstructed<InMemoryCumulativeDistributionSummary>().snapshot(any()) } returns summarySnapshot
            registry.timer(timerId)
            registry.counter(counterId)
            registry.gauge(gaugeId)
            registry.summary(summaryId)
            val instant = mockk<Instant>()

            // when
            val snapshots = registry.snapshots(instant)

            // then
            assertThat(snapshots).all {
                hasSize(4)
                containsAll(timerSnapshot, gaugeSnapshot, counterSnapshot, timerSnapshot)
            }
            coVerify {
                anyConstructed<InMemoryCumulativeTimer>().snapshot(refEq(instant))
                anyConstructed<InMemoryGauge>().snapshot(refEq(instant))
                anyConstructed<InMemoryCumulativeCounter>().snapshot(refEq(instant))
                anyConstructed<InMemoryCumulativeDistributionSummary>().snapshot(refEq(instant))
            }
        }

    @Test
    fun `should generate snapshots from all the registered meters duplicating for the global campaign when there is a unique scenario`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 1 })
            val timerId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.TIMER, emptyMap())
            val counterId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.COUNTER, emptyMap())
            val gaugeId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.GAUGE, emptyMap())
            val summaryId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.DISTRIBUTION_SUMMARY, emptyMap())
            mockkConstructor(
                InMemoryCumulativeTimer::class,
                InMemoryCumulativeDistributionSummary::class,
                InMemoryCumulativeCounter::class,
                InMemoryGauge::class
            )
            val timerSnapshot = mockk<MeterSnapshot> {
                every { meterId.tags.containsKey("scenario") } returns false
            }
            coEvery { anyConstructed<InMemoryCumulativeTimer>().snapshot(any()) } returns timerSnapshot
            val duplicatedGaugeSnapshot = mockk<MeterSnapshot>()
            val gaugeSnapshot = mockk<MeterSnapshot> {
                every { meterId } returns Meter.Id(
                    "the-gauge",
                    MeterType.GAUGE,
                    mapOf("a" to "b", "c" to "d", "scenario" to "the scenario")
                )
                every { duplicate(any()) } returns duplicatedGaugeSnapshot
            }
            coEvery { anyConstructed<InMemoryGauge>().snapshot(any()) } returns gaugeSnapshot
            val counterSnapshot = mockk<MeterSnapshot> {
                every { meterId.tags.containsKey("scenario") } returns false
            }
            coEvery { anyConstructed<InMemoryCumulativeCounter>().snapshot(any()) } returns counterSnapshot
            val duplicatedSummarySnapshot = mockk<MeterSnapshot>()
            val summarySnapshot = mockk<MeterSnapshot> {
                every { meterId } returns Meter.Id(
                    "the-summary",
                    MeterType.DISTRIBUTION_SUMMARY,
                    mapOf("c" to "d", "e" to "f", "scenario" to "the other scenario")
                )
                every { duplicate(any()) } returns duplicatedSummarySnapshot
            }
            coEvery { anyConstructed<InMemoryCumulativeDistributionSummary>().snapshot(any()) } returns summarySnapshot
            registry.timer(timerId)
            registry.counter(counterId)
            registry.gauge(gaugeId)
            registry.summary(summaryId)
            val instant = mockk<Instant>()

            // when
            val snapshots = registry.snapshots(instant)

            // then
            assertThat(snapshots).all {
                hasSize(6)
                containsAll(
                    timerSnapshot,
                    gaugeSnapshot,
                    counterSnapshot,
                    timerSnapshot,
                    duplicatedGaugeSnapshot,
                    duplicatedSummarySnapshot
                )
            }
            coVerify {
                anyConstructed<InMemoryCumulativeTimer>().snapshot(refEq(instant))
                anyConstructed<InMemoryGauge>().snapshot(refEq(instant))
                anyConstructed<InMemoryCumulativeCounter>().snapshot(refEq(instant))
                anyConstructed<InMemoryCumulativeDistributionSummary>().snapshot(refEq(instant))
                gaugeSnapshot.duplicate(meterId = Meter.Id("the-gauge", MeterType.GAUGE, mapOf("a" to "b", "c" to "d")))
                summarySnapshot.duplicate(
                    meterId = Meter.Id(
                        "the-summary",
                        MeterType.DISTRIBUTION_SUMMARY,
                        mapOf("c" to "d", "e" to "f")
                    )
                )
            }
        }

    @Test
    fun `should generate total snapshots from all the registered meters duplicating for the global campaign when there is a unique scenario`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 1 })
            val timerId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.TIMER, emptyMap())
            val counterId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.COUNTER, emptyMap())
            val gaugeId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.GAUGE, emptyMap())
            val summaryId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.DISTRIBUTION_SUMMARY, emptyMap())
            mockkConstructor(
                InMemoryCumulativeTimer::class,
                InMemoryCumulativeDistributionSummary::class,
                InMemoryCumulativeCounter::class,
                InMemoryGauge::class
            )
            val timerSnapshot = mockk<MeterSnapshot> {
                every { meterId.tags.containsKey("scenario") } returns false
            }
            coEvery { anyConstructed<InMemoryCumulativeTimer>().summarize(any()) } returns timerSnapshot
            val duplicatedGaugeSnapshot = mockk<MeterSnapshot>()
            val gaugeSnapshot = mockk<MeterSnapshot> {
                every { meterId } returns Meter.Id(
                    "the-gauge",
                    MeterType.GAUGE,
                    mapOf("a" to "b", "c" to "d", "scenario" to "the scenario")
                )
                every { duplicate(any()) } returns duplicatedGaugeSnapshot
            }
            coEvery { anyConstructed<InMemoryGauge>().summarize(any()) } returns gaugeSnapshot
            val counterSnapshot = mockk<MeterSnapshot> {
                every { meterId.tags.containsKey("scenario") } returns false
            }
            coEvery { anyConstructed<InMemoryCumulativeCounter>().summarize(any()) } returns counterSnapshot
            val duplicatedSummarySnapshot = mockk<MeterSnapshot>()
            val summarySnapshot = mockk<MeterSnapshot> {
                every { meterId } returns Meter.Id(
                    "the-summary",
                    MeterType.DISTRIBUTION_SUMMARY,
                    mapOf("c" to "d", "e" to "f", "scenario" to "the other scenario")
                )
                every { duplicate(any()) } returns duplicatedSummarySnapshot
            }
            coEvery { anyConstructed<InMemoryCumulativeDistributionSummary>().summarize(any()) } returns summarySnapshot
            registry.timer(timerId)
            registry.counter(counterId)
            registry.gauge(gaugeId)
            registry.summary(summaryId)
            val instant = mockk<Instant>()

            // when
            val snapshots = registry.summarize(instant)

            // then
            assertThat(snapshots).all {
                hasSize(6)
                containsAll(
                    timerSnapshot,
                    gaugeSnapshot,
                    counterSnapshot,
                    timerSnapshot,
                    duplicatedGaugeSnapshot,
                    duplicatedSummarySnapshot
                )
            }
            coVerify {
                anyConstructed<InMemoryCumulativeTimer>().summarize(refEq(instant))
                anyConstructed<InMemoryGauge>().summarize(refEq(instant))
                anyConstructed<InMemoryCumulativeCounter>().summarize(refEq(instant))
                anyConstructed<InMemoryCumulativeDistributionSummary>().summarize(refEq(instant))
                gaugeSnapshot.duplicate(meterId = Meter.Id("the-gauge", MeterType.GAUGE, mapOf("a" to "b", "c" to "d")))
                summarySnapshot.duplicate(
                    meterId = Meter.Id(
                        "the-summary",
                        MeterType.DISTRIBUTION_SUMMARY,
                        mapOf("c" to "d", "e" to "f")
                    )
                )
            }
        }

    @Test
    fun `should generate total snapshots from all the registered meters when there are several scenarios`() =
        testDispatcherProvider.runTest {
            // given
            registry.init(mockk { every { scenarios.size } returns 2 })
            val timerId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.TIMER, emptyMap())
            val counterId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.COUNTER, emptyMap())
            val gaugeId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.GAUGE, emptyMap())
            val summaryId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.DISTRIBUTION_SUMMARY, emptyMap())
            mockkConstructor(
                InMemoryCumulativeTimer::class,
                InMemoryCumulativeDistributionSummary::class,
                InMemoryCumulativeCounter::class,
                InMemoryGauge::class
            )
            val timerSnapshot = mockk<MeterSnapshot>()
            coEvery { anyConstructed<InMemoryCumulativeTimer>().summarize(any()) } returns timerSnapshot
            val gaugeSnapshot = mockk<MeterSnapshot>()
            coEvery { anyConstructed<InMemoryGauge>().summarize(any()) } returns gaugeSnapshot
            val counterSnapshot = mockk<MeterSnapshot>()
            coEvery { anyConstructed<InMemoryCumulativeCounter>().summarize(any()) } returns counterSnapshot
            val summarySnapshot = mockk<MeterSnapshot>()
            coEvery { anyConstructed<InMemoryCumulativeDistributionSummary>().summarize(any()) } returns summarySnapshot
            registry.timer(timerId)
            registry.counter(counterId)
            registry.gauge(gaugeId)
            registry.summary(summaryId)
            val instant = mockk<Instant>()

            // when
            val snapshots = registry.summarize(instant)

            // then
            assertThat(snapshots).all {
                hasSize(4)
                containsAll(timerSnapshot, gaugeSnapshot, counterSnapshot, timerSnapshot)
            }
            coVerify {
                anyConstructed<InMemoryCumulativeTimer>().summarize(refEq(instant))
                anyConstructed<InMemoryGauge>().summarize(refEq(instant))
                anyConstructed<InMemoryCumulativeCounter>().summarize(refEq(instant))
                anyConstructed<InMemoryCumulativeDistributionSummary>().summarize(refEq(instant))
            }
        }

    @Test
    fun `should clean the meters`() = testDispatcherProvider.runTest {
        // given
        val timerId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.TIMER, emptyMap())
        val counterId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.COUNTER, emptyMap())
        val gaugeId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.GAUGE, emptyMap())
        val summaryId = Meter.Id(RandomStringUtils.randomAlphabetic(3), MeterType.DISTRIBUTION_SUMMARY, emptyMap())

        registry.timer(timerId)
        registry.counter(counterId)
        registry.gauge(gaugeId)
        registry.summary(summaryId)
        assertThat(registry.meters()).hasSize(4)

        // when
        registry.close(mockk())

        // then
        assertThat(registry.meters()).isEmpty()
    }
}